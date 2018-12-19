import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import io.circe.generic.auto._
import io.circe.syntax._

import scala.collection.mutable.{Queue => QueueM}
import scala.concurrent.{ExecutionContextExecutor, Future}

trait Implicits {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
}

trait Settings {
  val sourceServiceHost = "127.0.0.1"
  val sourceServicePort = 5555
  val serverHost = "0.0.0.0"
  val serverPort = 5556
  val historyDepth = 10
  val periodLengthMillis = 60000L
}

trait Types {
  type Len = Short
  type TickerLen = Short
  type Size = Int
  type Ticker = String
  type Timestamp = Long
  type Price = Double
  type TimePeriod = Long
}

trait ImportModule {
  this: Settings with Types =>

  case class IncomingData(timestamp: Timestamp, ticker: Ticker, price: Price, size: Size)
  object IncomingData{
    import scodec._
    import scodec.bits._
    import scodec.codecs._
    //  LEN: длина последующего сообщения (целое, 2 байта)
    //  TIMESTAMP: дата и время события (целое, 8 байт, milliseconds since epoch)
    //  TICKER_LEN: длина биржевого тикера (целое, 2 байта)
    //  TICKER: биржевой тикер (ASCII, TICKER_LEN байт)
    //  PRICE: цена сделки (double, 8 байт)
    //  SIZE: объем сделки (целое, 4 байта)
    val baseCodec: Codec[IncomingData] = {
      ("len" | int16).unit(0) :~>:
        ("timestamp" | int64) ::
        ("ticker" | int16.consume(limitedSizeBytes(_,utf8))(_.length)) ::
        ("price" | double ) ::
        ("size" | int32)
    }.as[IncomingData]

    def apply(binary: ByteString): Option[IncomingData] =
      baseCodec.decode(BitVector(binary.toArray)).toOption.map(_.value)
  }
}


trait ExportModule {
  this: Types with LogicModule =>
  case class CandleJsonData(ticker: Ticker, timestamp: String, open: Price, high: Price, low: Price, close: Price, volume: Size)
  def toJson: (TimePeriod, Candles) => String = { (period, candles) =>
    val ts = Instant.ofEpochMilli(periodToMillis(period)).toString
    candles.map{
      case (ticker, Candle(open, high, low, close, volume)) => CandleJsonData(ticker,ts,open,high,low,close,volume)
    }.map(_.asJson.noSpaces)
      .mkString("\n")
  }
}

trait LogicModule {
  this: Settings with Types with ImportModule =>

  case class Candle(open: Price, high: Price, low: Price, close: Price, volume: Size) {
    def update(price: Price, size: Size): Candle =
      copy(open, high.max(price), low.min(price), price, volume = volume + size)
  }
  def Candle(price: Price, size: Size): Candle = Candle(price, price, price, price, size )

  type Candles = Map[Ticker, Candle]
  case class Aggregated(period: TimePeriod, candles: Map[Ticker, Candle], emit: Option[(TimePeriod,Candles)]) {
    def update: IncomingData => Aggregated = {
      case IncomingData(ts, ticker, price, size) =>
        if (period < periodFromMillis(ts))
          Aggregated(periodFromMillis(ts), Map(ticker -> Candle(price, size)), Some(period -> candles))
        else {
          val newOrUpdated =
            candles.get(ticker).map(_.update(price, size)).getOrElse(Candle(price, size))
          Aggregated(period, candles.updated(ticker, newOrUpdated), None)
        }

    }
  }

  def periodFromMillis(timestamp: Timestamp = System.currentTimeMillis()): TimePeriod = timestamp / periodLengthMillis
  def periodToMillis(timePeriod: TimePeriod): Long = timePeriod * periodLengthMillis

}

object Server extends App
  with ImportModule
  with Implicits
  with Settings
  with LogicModule
  with ExportModule
  with Types {

  val connection = Tcp().outgoingConnection(sourceServiceHost, sourceServicePort)

  val (pubSubSink, pubSubSource) =
    Source.queue[String](historyDepth,OverflowStrategy.dropHead)
      .toMat(BroadcastHub.sink[String])(Keep.both)
      .run()

  val incomingDataFlow =
    Flow.fromSinkAndSource(
      Flow[ByteString]
        .map(IncomingData(_))
        .collect{case Some(x) => x}
        .scan[Aggregated](Aggregated(0, Map.empty, None)){_.update(_)}
        .collect{ case Aggregated(_,_,Some(x)) => x}.drop(1)
        .map(toJson.tupled(_))
        .map(pubSubSink.offer)
        .to(Sink.ignore),
      Source.maybe
    )

  connection.join(incomingDataFlow).run()

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind(serverHost, serverPort)

  var history: QueueM[String] = QueueM.empty
  pubSubSource.runForeach{ x =>
    history.enqueue(x)
    if (history.size > historyDepth) history.dequeue()
  }

  connections runForeach { connection =>
    println(s"New connection from: ${connection.remoteAddress}")

    val outConnectionFlow: Flow[ByteString, ByteString, NotUsed] =
      Flow.fromSinkAndSource(
        Sink.ignore,
        pubSubSource.prepend(Source.single(history.toList)
          .map{ x=> println("Queue:"+ x); x}
          .mapConcat(identity)).map(ByteString(_))
      )
    connection.handleWith(outConnectionFlow)
  }
}

object Client extends App with Implicits with Settings {
  val connection = Tcp().outgoingConnection(serverHost, serverPort)
  val flow = Flow.fromSinkAndSource(Sink.foreach[ByteString](x => println(x.utf8String)), Source.maybe)
  connection.join(flow).run()
}

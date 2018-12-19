import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source, Tcp}
import akka.util.ByteString

import scala.collection.mutable.{Queue => QueueM}
import scala.concurrent.Future

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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor

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








import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString

object Client extends App with Implicits with Settings {
  val connection = Tcp().outgoingConnection(serverHost, serverPort)
  val flow = Flow.fromSinkAndSource(Sink.foreach[ByteString](x => println(x.utf8String)), Source.maybe)
  connection.join(flow).run()
}

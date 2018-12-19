import akka.util.ByteString

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

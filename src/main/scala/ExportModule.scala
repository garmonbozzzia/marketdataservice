import java.time.Instant

import io.circe.generic.auto._
import io.circe.syntax._


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

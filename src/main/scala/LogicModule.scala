

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

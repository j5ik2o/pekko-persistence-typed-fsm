package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import java.util.{Currency, Locale}
import _root_.scala.math.BigDecimal

/**
 * Class representing money.
 *
 * A class consisting of a certain "amount" and "currency unit".
 *
 * @author
 *   j5ik2o
 * @param amount
 *   Amount [[scala.math.BigDecimal]]
 * @param currency
 *   Currency unit [[java.util.Currency]]
 */
class Money(private val amount: BigDecimal, private val currency: Currency)
  extends Ordered[Money]
  with Serializable {

  require(
    amount.scale == currency.getDefaultFractionDigits,
    "Scale of amount does not match currency",
  )

  override def equals(obj: Any): Boolean = {
    import scala.compiletime.asMatchable
    obj.asMatchable match {
      case that: Money => amount == that.amount && currency == that.currency
      //    case bd: BigDecimal => amount == bd
      //    case n: Int => amount == n
      //    case f: Float => amount == f
      //    case d: Double => amount == d
      case _ => false
    }
  }

  override def hashCode: Int = 31 * (amount.hashCode + currency.hashCode)

  /**
   * Returns a [[org.sisioh.baseunits.scala.money.Money]] whose amount is the absolute amount of
   * this [[org.sisioh.baseunits.scala.money.Money]], and whose scale is this.scale().
   *
   * @return
   *   Absolute amount
   */
  lazy val abs: Money = Money(amount.abs, currency)

  /**
   * Compare amounts with each other.
   *
   * The one with relatively smaller amount is judged as "smaller". If the currency units are different, 
   * [[java.lang.ClassCastException]] will be thrown, but if either amount is `0`, no exception will be thrown.
   *
   * For example, `10 USD` and `0 JPY`, the latter is smaller. Also, `0 USD` and `0 JPY` are the same.
   *
   * @param that
   *   Comparison target
   * @return
   *   According to `Comparable.compareTo(Object)`
   */
  override def compare(that: Money): Int = {
    require(currency == that.currency)
    amount compare that.amount
  }

  def /(divisor: Double): Money = dividedBy(divisor)

  def *(other: BigDecimal): Money = times(other)

  def +(other: Money): Money = {
    require(currency == other.currency)
    plus(other)
  }

  def -(other: Money): Money = {
    require(currency == other.currency)
    minus(other)
  }

  /**
   * Returns the `amount` field (quantity) of this object.
   *
   * CAUTION: This method exposes elements that this object encapsulates. Handle with sufficient care.
   *
   * How best to handle access to the internals? It is needed for database mapping, UI presentation,
   * and perhaps a few other uses. Yet giving public access invites people to do the real work of
   * the Money object elsewhere. Here is an experimental approach, giving access with a warning
   * label of sorts. Let us know how you like it.
   *
   * @return
   *   Amount
   */
  val breachEncapsulationOfAmount: BigDecimal = amount

  /**
   * Returns the `currency` field (currency unit) of this object.
   *
   * CAUTION: This method exposes elements that this object encapsulates. Handle with sufficient care.
   *
   * @return
   *   Currency unit
   */
  val breachEncapsulationOfCurrency: Currency = currency

  /**
   * Returns the amount when this amount is evenly divided into `divisor` parts.
   *
   * Applies rounding mode `RoundingMode#HALF_EVEN`.
   *
   * @param divisor
   *   Divisor
   * @return
   *   Amount
   */
  def dividedBy(divisor: Double): Money =
    dividedBy(divisor, Money.DefaultRoundingMode)

  /**
   * Returns the amount when this amount is evenly divided into `divisor` parts.
   *
   * @param divisor
   *   Divisor
   * @param roundingMode
   *   Rounding mode
   * @return
   *   Amount
   */
  def dividedBy(divisor: BigDecimal, roundingMode: BigDecimal.RoundingMode.Value): Money = {
    val newAmount = amount / divisor
    Money(newAmount.setScale(currency.getDefaultFractionDigits, roundingMode), currency)
  }

  /**
   * Returns the amount when this amount is evenly divided into `divisor` parts.
   *
   * @param divisor
   *   Divisor
   * @param roundingMode
   *   Rounding mode
   * @return
   *   Amount
   */
  def dividedBy(divisor: Double, roundingMode: BigDecimal.RoundingMode.Value): Money =
    dividedBy(BigDecimal(divisor), roundingMode)

  /**
   * Check if the amount represented by this instance is greater than `other`.
   *
   * If equivalent, return `false`.
   *
   * @param other
   *   Reference amount
   * @return
   *   `true` if greater, `false` otherwise
   * @throws ClassCastException
   *   If the currency unit of the argument is different from the currency unit of this instance
   */
  def isGreaterThan(other: Money): Boolean =
    this > other

  /**
   * Check if the amount represented by this instance is less than `other`.
   *
   * If equivalent, return `false`.
   *
   * @param other
   *   Reference amount
   * @return
   *   `true` if less, `false` otherwise
   * @throws ClassCastException
   *   If the currency unit of the argument is different from the currency unit of this instance
   */
  def isLessThan(other: Money): Boolean = this < other

  /**
   * Check if the amount represented by this instance is negative.
   *
   * If zero, return `false`.
   *
   * @return
   *   `true` if negative, `false` otherwise
   */
  lazy val isNegative: Boolean = amount < BigDecimal(0)

  /**
   * Check if the amount represented by this instance is positive.
   *
   * If zero, return `false`.
   *
   * @return
   *   `true` if positive, `false` otherwise
   */
  lazy val isPositive: Boolean = amount > BigDecimal(0)

  /**
   * Check if the amount represented by this instance is zero.
   *
   * @return
   *   `true` if zero, `false` otherwise
   */
  lazy val isZero: Boolean =
    equals(Money.adjustBy(0.0, currency))

  /**
   * Returns the amount after subtracting `other` from this amount.
   *
   * @param other
   *   Amount
   * @return
   *   Subtracted amount
   * @throws ClassCastException
   *   If the currency unit of the argument is different from the currency unit of this instance
   */
  def minus(other: Money): Money =
    plus(other.negated)

  /**
   * Returns a `Money` whose amount is (-amount), and whose scale is this.scale().
   *
   * @return
   *   Amount
   */
  lazy val negated: Money =
    Money(BigDecimal(amount.bigDecimal.negate), currency)

  /**
   * Returns the amount after adding `other` to this amount.
   *
   * @param other
   *   Amount
   * @return
   *   Added amount
   * @throws ClassCastException
   *   If the currency unit of the argument is different from the currency unit of this instance
   */
  def plus(other: Money): Money = {
    checkHasSameCurrencyAs(other)
    Money.adjustBy(amount + other.amount, currency)
  }

  /**
   * Returns the amount after multiplying this amount by `factor`.
   *
   * Applies rounding mode `RoundingMode#HALF_EVEN`.
   *
   * TODO: Many apps require carrying extra precision in intermediate calculations. The use of Ratio
   * is a beginning, but need a comprehensive solution. Currently, an invariant of Money is that the
   * scale is the currencies standard scale, but this will probably have to be suspended or
   * elaborated in intermediate calcs, or handled with defered calculations like Ratio.
   *
   * @param factor
   *   Coefficient
   * @return
   *   Multiplied amount
   */
  def times(factor: BigDecimal): Money =
    times(factor, Money.DefaultRoundingMode)

  /**
   * Returns the amount after multiplying this amount by `factor`.
   *
   * TODO: BigDecimal.multiply() scale is sum of scales of two multiplied numbers. So what is scale
   * of times?
   *
   * @param factor
   *   Coefficient
   * @param roundingMode
   *   Rounding mode
   * @return
   *   Multiplied amount
   */
  def times(factor: BigDecimal, roundingMode: BigDecimal.RoundingMode.Value): Money =
    Money.adjustBy(amount * factor, currency, roundingMode)

  /**
   * Returns the amount after multiplying this amount by `amount`.
   *
   * Applies rounding mode `RoundingMode#HALF_EVEN`.
   *
   * @param amount
   *   Coefficient
   * @return
   *   Multiplied amount
   */
  def times(amount: Double): Money =
    times(BigDecimal(amount))

  /**
   * Returns the amount after multiplying this amount by `amount`.
   *
   * @param amount
   *   Coefficient
   * @param roundingMode
   *   Rounding mode
   * @return
   *   Multiplied amount
   */
  def times(amount: Double, roundingMode: BigDecimal.RoundingMode.Value): Money =
    times(BigDecimal(amount), roundingMode)

  /**
   * Returns the amount after multiplying this amount by `amount`.
   *
   * Applies rounding mode `RoundingMode#HALF_EVEN`.
   *
   * @param amount
   *   Coefficient
   * @return
   *   Multiplied amount
   */
  def times(amount: Int): Money =
    times(BigDecimal(amount))

  override def toString: String =
    currency.getSymbol + " " + amount

  /**
   * Returns a string representation of the amount with unit in the specified locale.
   *
   * @param localeOption
   *   `Option` of locale. If `None`, uses `Locale#getDefault()`.
   * @return
   *   String representation of the amount
   */
  def toString(localeOption: Option[Locale]): String = {
    def createStrng(_locale: Locale) =
      currency.getSymbol(_locale) + " " + amount
    localeOption match {
      case Some(locale) => createStrng(locale)
      case None => createStrng(Locale.getDefault)
    }
  }

  private[example] def hasSameCurrencyAs(arg: Money): Boolean =
    currency.equals(arg.currency) || arg.amount.equals(BigDecimal(0)) || amount
      .equals(BigDecimal(0))

  /**
   * Returns the amount after adding the minimum unit amount to this amount, 
   * i.e., an amount that is 1 step larger than this amount.
   *
   * @return
   *   An amount 1 step larger than this amount
   */
  private[example] lazy val incremented: Money = plus(minimumIncrement)

  /**
   * Returns the minimum unit amount.
   *
   * For example, Japanese yen is 1 yen, and US$ is 1 cent (i.e., 0.01 dollars).
   *
   * This probably should be Currency responsibility. Even then, it may need to be customized for
   * specialty apps because there are other cases, where the smallest increment is not the smallest
   * unit.
   *
   * @return
   *   Minimum unit amount
   */
  private[example] lazy val minimumIncrement: Money = {
    val increment =
      BigDecimal(1).bigDecimal.movePointLeft(currency.getDefaultFractionDigits)
    Money(BigDecimal(increment), currency)
  }

  private def checkHasSameCurrencyAs(aMoney: Money): Unit =
    if (!hasSameCurrencyAs(aMoney)) {
      throw new ClassCastException(
        aMoney.toString + " is not same currency as " + this.toString,
      )
    }

}

/**
 * `Money` companion object.
 *
 * @author
 *   j5ik2o
 */
object Money {

  // implicit def bigDecimalToMoney(amount: Int) = apply(amount)

  val USD: Currency = Currency.getInstance("USD")

  val EUR: Currency = Currency.getInstance("EUR")

  val JPY: Currency = Currency.getInstance("JPY")

  val DefaultRoundingMode: BigDecimal.RoundingMode.Value =
    BigDecimal.RoundingMode.HALF_EVEN

  def apply(amount: BigDecimal, currency: Currency): Money =
    new Money(amount, currency)

  def unapply(money: Money): Option[(BigDecimal, Currency)] =
    Some(money.amount, money.currency)

  /**
   * Returns an instance representing the amount of dollars expressed by `amount`.
   *
   * This creation method is safe to use. It will adjust scale, but will not round off the amount.
   *
   * @param amount
   *   Amount
   * @return
   *   Instance representing the amount of dollars expressed by `amount`
   */
  def dollars(amount: BigDecimal): Money = adjustBy(amount, USD)

  /**
   * Returns an instance representing the amount of dollars expressed by `amount`.
   *
   * WARNING: Because of the indefinite precision of double, this method must round off the value.
   *
   * @param amount
   *   Amount
   * @return
   *   Instance representing the amount of dollars expressed by `amount`
   */
  def dollars(amount: Double): Money = adjustBy(amount, USD)

  /**
   * This creation method is safe to use. It will adjust scale, but will not round off the amount.
   * @param amount
   *   Amount
   * @return
   *   Instance representing the amount of euros expressed by `amount`
   */
  def euros(amount: BigDecimal): Money = adjustBy(amount, EUR)

  /**
   * WARNING: Because of the indefinite precision of double, this method must round off the value.
   * @param amount
   *   Amount
   * @return
   *   Instance representing the amount of euros expressed by `amount`
   */
  def euros(amount: Double): Money = adjustBy(amount, EUR)

  /**
   * Returns the total amount of all amounts contained in [[scala.Iterable]].
   *
   * The currency unit of the total amount will be the (common) currency unit of the elements of `monies`, 
   * but if the `Collection` is empty, it returns an instance with an amount of 0 in the currency unit of the current default locale.
   *
   * @param monies
   *   Collection of amounts
   * @return
   *   Total amount
   * @throws ClassCastException
   *   If the argument contains currency units with different currency units. However, no exception is thrown for amounts of 0 because currency units are not considered.
   */
  def sum(monies: Iterable[Money]): Money =
    if (monies.isEmpty) {
      Money.zero(Currency.getInstance(Locale.getDefault))
    } else {
      monies.reduceLeft(_ + _)
    }

  /**
   * This creation method is safe to use. It will adjust scale, but will not round off the amount.
   *
   * @param amount
   *   Amount
   * @param currency
   *   Currency unit
   * @return
   *   Amount
   */
  def adjustBy(amount: BigDecimal, currency: Currency): Money =
    adjustBy(amount, currency, BigDecimal.RoundingMode.UNNECESSARY)

  /**
   * For convenience, an amount can be rounded to create a Money.
   *
   * @param rawAmount
   *   Amount
   * @param currency
   *   Currency unit
   * @param roundingMode
   *   Rounding mode
   * @return
   *   Amount
   */
  def adjustBy(
    rawAmount: BigDecimal,
    currency: Currency,
    roundingMode: BigDecimal.RoundingMode.Value): Money = {
    val amount =
      rawAmount.setScale(currency.getDefaultFractionDigits, roundingMode)
    new Money(amount, currency)
  }

  /**
   * WARNING: Because of the indefinite precision of double, this method must round off the value.
   *
   * @param dblAmount
   *   Amount
   * @param currency
   *   Currency unit
   * @return
   *   Amount
   */
  def adjustBy(dblAmount: Double, currency: Currency): Money =
    adjustBy(dblAmount, currency, DefaultRoundingMode)

  /**
   * Because of the indefinite precision of double, this method must round off the value. This
   * method gives the client control of the rounding mode.
   *
   * @param dblAmount
   *   Amount
   * @param currency
   *   Currency unit
   * @param roundingMode
   *   Rounding mode
   * @return
   *   Amount
   */
  def adjustRound(
    dblAmount: Double,
    currency: Currency,
    roundingMode: BigDecimal.RoundingMode.Value): Money = {
    val rawAmount = BigDecimal(dblAmount)
    adjustBy(rawAmount, currency, roundingMode)
  }

  /**
   * This creation method is safe to use. It will adjust scale, but will not round off the amount.
   *
   * @param amount
   *   Amount
   * @return
   *   Instance representing the amount of yen expressed by `amount`
   */
  def yens(amount: BigDecimal): Money = adjustBy(amount, JPY)

  /**
   * WARNING: Because of the indefinite precision of double, this method must round off the value.
   *
   * @param amount
   *   Amount
   * @return
   *   Instance representing the amount of yen expressed by `amount`
   */
  def yens(amount: Double): Money = adjustBy(amount, JPY)

  /**
   * Returns an amount of 0 with the specified currency unit.
   *
   * @param currency
   *   Currency unit
   * @return
   *   Amount
   */
  def zero(currency: Currency): Money = adjustBy(0.0, currency)

}

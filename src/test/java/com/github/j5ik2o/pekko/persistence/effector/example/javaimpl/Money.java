package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * 金額を表すクラス。
 *
 * ある一定の「量」と「通貨単位」から成るクラスである。
 */
public class Money implements Comparable<Money>, Serializable {
    private final BigDecimal amount;
    private final Currency currency;

    public static final Currency USD = Currency.getInstance("USD");
    public static final Currency EUR = Currency.getInstance("EUR");
    public static final Currency JPY = Currency.getInstance("JPY");
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private Money(BigDecimal amount, Currency currency) {
        if (amount.scale() != currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException("Scale of amount does not match currency");
        }
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return adjustBy(amount, currency);
    }

    public static Money dollars(BigDecimal amount) {
        return adjustBy(amount, USD);
    }

    public static Money dollars(double amount) {
        return adjustBy(amount, USD);
    }

    public static Money euros(BigDecimal amount) {
        return adjustBy(amount, EUR);
    }

    public static Money euros(double amount) {
        return adjustBy(amount, EUR);
    }

    public static Money yens(BigDecimal amount) {
        return adjustBy(amount, JPY);
    }

    public static Money yens(double amount) {
        return adjustBy(amount, JPY);
    }

    public static Money zero(Currency currency) {
        return adjustBy(0.0, currency);
    }

    public static Money adjustBy(BigDecimal amount, Currency currency) {
        return adjustBy(amount, currency, RoundingMode.UNNECESSARY);
    }

    public static Money adjustBy(BigDecimal rawAmount, Currency currency, RoundingMode roundingMode) {
        BigDecimal amount = rawAmount.setScale(currency.getDefaultFractionDigits(), roundingMode);
        return new Money(amount, currency);
    }

    public static Money adjustBy(double dblAmount, Currency currency) {
        return adjustRound(dblAmount, currency, DEFAULT_ROUNDING_MODE);
    }

    public static Money adjustRound(double dblAmount, Currency currency, RoundingMode roundingMode) {
        BigDecimal rawAmount = BigDecimal.valueOf(dblAmount);
        return adjustBy(rawAmount, currency, roundingMode);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    @Override
    public int compareTo(Money that) {
        if (!currency.equals(that.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies");
        }
        return amount.compareTo(that.amount);
    }

    public Money dividedBy(double divisor) {
        return dividedBy(divisor, DEFAULT_ROUNDING_MODE);
    }

    public Money dividedBy(BigDecimal divisor, RoundingMode roundingMode) {
        BigDecimal newAmount = amount.divide(divisor, roundingMode);
        return new Money(newAmount.setScale(currency.getDefaultFractionDigits(), roundingMode), currency);
    }

    public Money dividedBy(double divisor, RoundingMode roundingMode) {
        return dividedBy(BigDecimal.valueOf(divisor), roundingMode);
    }

    public boolean isGreaterThan(Money other) {
        return this.compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return this.compareTo(other) < 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return equals(Money.adjustBy(0.0, currency));
    }

    public Money minus(Money other) {
        return plus(other.negated());
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money plus(Money other) {
        checkHasSameCurrencyAs(other);
        return Money.adjustBy(amount.add(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        return times(factor, DEFAULT_ROUNDING_MODE);
    }

    public Money times(BigDecimal factor, RoundingMode roundingMode) {
        return Money.adjustBy(amount.multiply(factor), currency, roundingMode);
    }

    public Money times(double amount) {
        return times(BigDecimal.valueOf(amount));
    }

    public Money times(double amount, RoundingMode roundingMode) {
        return times(BigDecimal.valueOf(amount), roundingMode);
    }

    public Money times(int amount) {
        return times(BigDecimal.valueOf(amount));
    }

    @Override
    public String toString() {
        return currency.getSymbol() + " " + amount;
    }

    public String toString(Locale locale) {
        return currency.getSymbol(locale) + " " + amount;
    }

    public boolean hasSameCurrencyAs(Money arg) {
        return currency.equals(arg.currency) || arg.amount.equals(BigDecimal.ZERO) || amount.equals(BigDecimal.ZERO);
    }

    public Money incremented() {
        return plus(minimumIncrement());
    }

    public Money minimumIncrement() {
        BigDecimal increment = BigDecimal.ONE.movePointLeft(currency.getDefaultFractionDigits());
        return new Money(increment, currency);
    }

    private void checkHasSameCurrencyAs(Money aMoney) {
        if (!hasSameCurrencyAs(aMoney)) {
            throw new IllegalArgumentException(aMoney + " is not same currency as " + this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount) && Objects.equals(currency, money.currency);
    }

    @Override
    public int hashCode() {
        return 31 * (amount.hashCode() + currency.hashCode());
    }
}

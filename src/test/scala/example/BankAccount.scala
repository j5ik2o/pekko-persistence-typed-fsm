package com.github.j5ik2o.pekko.persistence.typed.fsm
package example

import java.time.Instant

sealed trait BankAccountError
object BankAccountError {
  case object LimitOverError extends BankAccountError
  case object InsufficientFundsError extends BankAccountError
}

final case class Result[S, E](
  bankAccount: S,
  event: E,
)

final case class BankAccount(
  bankAccountId: BankAccountId,
  limit: Money = Money(100000, Money.JPY),
  balance: Money = Money(0, Money.JPY),
) {

  def add(amount: Money): Either[BankAccountError, Result[BankAccount, BankAccountEvent]] =
    if (limit < (balance + amount))
      Left(BankAccountError.LimitOverError)
    else
      Right(
        Result(
          copy(balance = balance + amount),
          BankAccountEvent.CashDeposited(bankAccountId, amount, Instant.now())))

  def subtract(amount: Money): Either[BankAccountError, Result[BankAccount, BankAccountEvent]] =
    if (Money(0, Money.JPY) > (balance - amount))
      Left(BankAccountError.LimitOverError)
    else
      Right(
        Result(
          copy(balance = balance - amount),
          BankAccountEvent.CashWithdrew(bankAccountId, amount, Instant.now())))

}

object BankAccount {
  def apply(bankAccountId: BankAccountId): BankAccount =
    new BankAccount(bankAccountId)

  def create(
    bankAccountId: BankAccountId,
    limit: Money = Money(100000, Money.JPY),
    balance: Money = Money(0, Money.JPY),
  ): Result[BankAccount, BankAccountEvent] =
    Result(
      new BankAccount(bankAccountId, limit, balance),
      BankAccountEvent.Created(bankAccountId, Instant.now()))
}

package com.github.j5ik2o.eff.sm.splitter
package example

import org.apache.pekko.actor.typed.ActorRef

enum BankAccountCommand {
  case GetBalance(override val aggregateId: BankAccountId, replyTo: ActorRef[GetBalanceReply])
  case Create(override val aggregateId: BankAccountId, replyTo: ActorRef[CreateReply])
  case DepositCash(
    override val aggregateId: BankAccountId,
    amount: Money,
    replyTo: ActorRef[DepositCashReply])
  case WithdrawCash(
    override val aggregateId: BankAccountId,
    amount: Money,
    replyTo: ActorRef[WithdrawCashReply])

  private case StateRecovered(state: BankAccountAggregate.State)
    extends BankAccountCommand
    with WrappedRecovered[BankAccountAggregate.State, BankAccountCommand]
  private case EventPersisted(state: BankAccountAggregate.State, events: Seq[BankAccountEvent])
    extends BankAccountCommand
    with WrappedPersisted[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand]

  def aggregateId: BankAccountId = this match {
    case GetBalance(aggregateId, _) => aggregateId
    case Create(aggregateId, _) => aggregateId
    case DepositCash(aggregateId, _, _) => aggregateId
    case WithdrawCash(aggregateId, _, _) => aggregateId
    case StateRecovered(state) => state.aggregateId
    case EventPersisted(state, _) => state.aggregateId
  }
}

object BankAccountCommand
  extends WrappedSupportProtocol[BankAccountAggregate.State, BankAccountEvent] {
  override type Message = BankAccountCommand
  def wrappedISO: WrappedISO[BankAccountAggregate.State, BankAccountEvent, Message] =
    WrappedISO(EventPersisted.apply, StateRecovered.apply)
}

enum GetBalanceReply {
  case Succeeded(aggregateId: BankAccountId, balance: Money)
}

enum CreateReply {
  case Succeeded(aggregateId: BankAccountId)
}

enum DepositCashReply {
  case Succeeded(aggregateId: BankAccountId, amount: Money)
  case Failed(aggregateId: BankAccountId, error: BankAccountError)
}

enum WithdrawCashReply {
  case Succeeded(aggregateId: BankAccountId, amount: Money)
  case Failed(aggregateId: BankAccountId, error: BankAccountError)
}

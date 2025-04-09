package com.github.j5ik2o.eff.sm.splitter
package example

import org.apache.pekko.actor.typed.ActorRef

enum BankAccountCommand {
  case GetBalance(override val aggregateId: BankAccountId, replyTo: ActorRef[GetBalanceReply])
  case Stop(override val aggregateId: BankAccountId, replyTo: ActorRef[StopReply])
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
    with RecoveredState[BankAccountAggregate.State, BankAccountCommand]
  private case EventPersisted(events: Seq[BankAccountEvent])
    extends BankAccountCommand
    with PersistedEvent[BankAccountEvent, BankAccountCommand]

  def aggregateId: BankAccountId = this match {
    case GetBalance(aggregateId, _) => aggregateId
    case Stop(aggregateId, _) => aggregateId
    case Create(aggregateId, _) => aggregateId
    case DepositCash(aggregateId, _, _) => aggregateId
    case WithdrawCash(aggregateId, _, _) => aggregateId
    case StateRecovered(state) => state.aggregateId
    case EventPersisted(_) =>
      throw new UnsupportedOperationException("EventPersisted does not have aggregateId")
  }
}

object BankAccountCommand extends MessageProtocol[BankAccountAggregate.State, BankAccountEvent] {
  override type Message = BankAccountCommand
  def messageConverter: MessageConverter[BankAccountAggregate.State, BankAccountEvent, Message] =
    MessageConverter(EventPersisted.apply, StateRecovered.apply)
}

enum StopReply {
  case Succeeded(aggregateId: BankAccountId)
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

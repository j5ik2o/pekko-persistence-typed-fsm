package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.example.*
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.*
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
  private case StatePersisted(state: BankAccountAggregate.State)
    extends BankAccountCommand
    with PersistedState[BankAccountAggregate.State, BankAccountCommand]
  private case SnapshotShotsDeleted(maxSequenceNumber: Long)
    extends BankAccountCommand
    with DeletedSnapshots[BankAccountCommand]

  def aggregateId: BankAccountId = this match {
    case GetBalance(aggregateId, _) => aggregateId
    case Stop(aggregateId, _) => aggregateId
    case Create(aggregateId, _) => aggregateId
    case DepositCash(aggregateId, _, _) => aggregateId
    case WithdrawCash(aggregateId, _, _) => aggregateId
    case StateRecovered(state) => state.aggregateId
    case _ =>
      throw new UnsupportedOperationException(
        "EventPersisted or StatePersisted or SnapshotShotsDeleted does not have aggregateId")
  }
}

object BankAccountCommand extends MessageProtocol[BankAccountAggregate.State, BankAccountEvent] {
  override type Message = BankAccountCommand
  def messageConverter: MessageConverter[BankAccountAggregate.State, BankAccountEvent, Message] =
    MessageConverter(
      EventPersisted.apply,
      StatePersisted.apply,
      StateRecovered.apply,
      SnapshotShotsDeleted.apply)
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

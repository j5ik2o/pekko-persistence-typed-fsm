package com.github.j5ik2o.pekko.persistence.typed.fsm
package example

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object BankAccountAggregate {
  def actorName(aggregateId: BankAccountId): String =
    s"${aggregateId.aggregateTypeName}-${aggregateId.asString}"

  enum State {
    def aggregateId: BankAccountId
    case NotCreated(aggregateId: BankAccountId)
    case Created(aggregateId: BankAccountId, bankAccount: BankAccount)

    def applyEvent(event: BankAccountEvent): State = (this, event) match {
      case (State.NotCreated(aggregateId), BankAccountEvent.Created(id, _)) =>
        Created(id, BankAccount(id))
      case (State.Created(id, bankAccount), BankAccountEvent.CashDeposited(_, amount, _)) =>
        bankAccount
          .add(amount)
          .fold(
            error => throw new IllegalStateException(s"Failed to apply event: $error"),
            result => State.Created(id, result._1),
          )
      case (State.Created(id, bankAccount), BankAccountEvent.CashWithdrew(_, amount, _)) =>
        bankAccount
          .subtract(amount)
          .fold(
            error => throw new IllegalStateException(s"Failed to apply event: $error"),
            result => State.Created(id, result._1),
          )
      case _ =>
        throw new IllegalStateException(
          s"Invalid state transition: $this -> $event",
        )
    }
  }

  def apply(
    aggregateId: BankAccountId,
  ): Behavior[BankAccountCommand] = {
    val config =
      PersistenceEffectorConfig[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
        persistenceId = actorName(aggregateId),
        initialState = State.NotCreated(aggregateId),
        applyEvent = (state, event) => state.applyEvent(event),
        messageConverter = BankAccountCommand.messageConverter,
      )
    Behaviors.setup[BankAccountCommand] { implicit ctx =>
      PersistenceEffector.create[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
        config,
      ) {
        case (initialState: State.NotCreated, effector) =>
          handleNotCreated(initialState, effector)
        case (initialState: State.Created, effector) =>
          handleCreated(initialState, effector)
      }
    }
  }

  private def handleNotCreated(
    state: BankAccountAggregate.State.NotCreated,
    effector: PersistenceEffector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
    : Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial { case cmd: BankAccountCommand.Create =>
      val Result(bankAccount, event) = BankAccount.create(cmd.aggregateId)
      effector.persistEvent(event) { _ =>
        cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
        handleCreated(State.Created(state.aggregateId, bankAccount), effector)
      }
    }

  private def handleCreated(
    state: BankAccountAggregate.State.Created,
    effector: PersistenceEffector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
    : Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial {
      case BankAccountCommand.Stop(aggregateId, replyTo) =>
        replyTo ! StopReply.Succeeded(aggregateId)
        Behaviors.stopped
      case BankAccountCommand.GetBalance(aggregateId, replyTo) =>
        replyTo ! GetBalanceReply.Succeeded(aggregateId, state.bankAccount.balance)
        Behaviors.same
      case BankAccountCommand.DepositCash(aggregateId, amount, replyTo) =>
        state.bankAccount
          .add(amount)
          .fold(
            error => {
              replyTo ! DepositCashReply.Failed(aggregateId, error)
              Behaviors.same
            },
            { case Result(newBankAccount, event) =>
              effector.persistEvent(event) { _ =>
                replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
                handleCreated(state.copy(bankAccount = newBankAccount), effector)
              }
            },
          )
      case BankAccountCommand.WithdrawCash(aggregateId, amount, replyTo) =>
        state.bankAccount
          .subtract(amount)
          .fold(
            error => {
              replyTo ! WithdrawCashReply.Failed(aggregateId, error)
              Behaviors.same
            },
            { case Result(newBankAccount, event) =>
              effector.persistEvent(event) { _ =>
                replyTo ! WithdrawCashReply.Succeeded(aggregateId, amount)
                handleCreated(state.copy(bankAccount = newBankAccount), effector)
              }
            },
          )
    }
}

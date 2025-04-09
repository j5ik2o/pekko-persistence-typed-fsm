package com.github.j5ik2o.eff.sm.splitter
package example

import example.BankAccountAggregate.State.NotCreated

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
    val config = EffectorConfig[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
      persistenceId = actorName(aggregateId),
      initialState = State.NotCreated(aggregateId),
      applyEvent = (state, event) => state.applyEvent(event),
      wrappedISO = BankAccountCommand.wrappedISO,
    )
    Behaviors.setup[BankAccountCommand] { implicit ctx =>
      Effector.create[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
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
    created: BankAccountAggregate.State.NotCreated,
    effector: Effector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
    : Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial { case cmd: BankAccountCommand.Create =>
      val (_, event) = BankAccount.create(cmd.aggregateId)
      effector.persist(event) {
        case (newState: BankAccountAggregate.State.Created, _) =>
          cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
          handleCreated(newState, effector)
        case _ => Behaviors.unhandled
      }
    }

  private def handleCreated(
    state: BankAccountAggregate.State.Created,
    effector: Effector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
    : Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial {
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
            { case (_, event) =>
              effector.persist(event) {
                case (newState: BankAccountAggregate.State.Created, _) =>
                  replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
                  handleCreated(newState, effector)
                case _ => Behaviors.unhandled
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
            { case (_, event) =>
              effector.persist(event) {
                case (newState: BankAccountAggregate.State.Created, _) =>
                  replyTo ! WithdrawCashReply.Succeeded(aggregateId, amount)
                  handleCreated(newState, effector)
                case _ => Behaviors.unhandled
              }
            },
          )
    }
}

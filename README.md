# pekko-persistence-typed-fsm

A library for efficient implementation of event sourcing and state transitions with Apache Pekko.

*Read this in other languages: [日本語](README.ja.md)*

## Overview

`pekko-persistence-typed-fsm` is a library that improves the implementation of event sourcing patterns using Apache Pekko. It eliminates the constraints of traditional Pekko Persistence Typed and enables event sourcing with a more intuitive actor programming style.

### Key Features

- **Traditional Actor Programming Style**: Enables event sourcing while maintaining the usual Behavior-based actor programming style
- **Single Execution of Domain Logic**: Eliminates the problem of double execution of domain logic in command handlers
- **High Compatibility with DDD**: Supports seamless integration with domain objects
- **Incremental Implementation**: Start with in-memory mode during development, then migrate to persistence later
- **Type Safety**: Type-safe design utilizing Scala 3's type system

## Background: Why This Library is Needed

Traditional Pekko Persistence Typed has the following issues:

1. **Inconsistency with Traditional Actor Programming Style**: Difficulty in learning and implementation
2. **Reduced Maintainability with Complex State Transitions**: Code maintainability decreases due to complex match/case statements
3. **Double Execution of Domain Logic**: Domain logic is executed in both command handlers and event handlers

This library solves these problems by implementing "Persistent Actor as a child actor of the aggregate actor."

## Main Components

### PersistenceEffector

A core trait that provides event persistence functionality.

```scala
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
}
```

### PersistenceEffectorConfig

A case class that defines the configuration for PersistenceEffector.

```scala
final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  messageConverter: MessageConverter[S, E, M]
)
```

### MessageConverter

A trait that defines the conversions between state (S), event (E), and message (M).

```scala
trait MessageConverter[S, E, M <: Matchable] {
  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedState(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]
  // ...
}
```

## Usage Examples

### BankAccount Example

```scala
// 1. Define state and state transition function
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
      throw new IllegalStateException(s"Invalid state transition: $this -> $event")
  }
}

// 2. Configure PersistenceEffectorConfig
val config = PersistenceEffectorConfig[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
  persistenceId = actorName(aggregateId),
  initialState = State.NotCreated(aggregateId),
  applyEvent = (state, event) => state.applyEvent(event),
  messageConverter = BankAccountCommand.messageConverter,
)

// 3. Create an actor using PersistenceEffector
Behaviors.setup[BankAccountCommand] { implicit ctx =>
  PersistenceEffector.create[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](config) {
    case (initialState: State.NotCreated, effector) =>
      handleNotCreated(initialState, effector)
    case (initialState: State.Created, effector) =>
      handleCreated(initialState, effector)
  }
}

// 4. Implement handlers according to state
private def handleCreated(
  state: BankAccountAggregate.State.Created,
  effector: PersistenceEffector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
  : Behavior[BankAccountCommand] =
  Behaviors.receiveMessagePartial {
    case BankAccountCommand.DepositCash(aggregateId, amount, replyTo) =>
      // Execute domain logic
      state.bankAccount
        .add(amount)
        .fold(
          error => {
            replyTo ! DepositCashReply.Failed(aggregateId, error)
            Behaviors.same
          },
          { case (newBankAccount, event) =>
            // Persist the event
            effector.persistEvent(event) { _ =>
              replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
              // Update with new state
              handleCreated(state.copy(bankAccount = newBankAccount), effector)
            }
          },
        )
  }
```

## Example Code Files

For more detailed implementation examples, see the following files:

- [BankAccountAggregate](src/test/scala/example/BankAccountAggregate.scala) - Main aggregate implementation using PersistenceEffector
- [BankAccount](src/test/scala/example/BankAccount.scala) - Domain model for bank account
- [BankAccountCommand](src/test/scala/example/BankAccountCommand.scala) - Commands for the aggregate
- [BankAccountEvent](src/test/scala/example/BankAccountEvent.scala) - Events produced by the aggregate
- [BankAccountId](src/test/scala/example/BankAccountId.scala) - Identifier for bank accounts
- [Money](src/test/scala/example/Money.scala) - Value object representing monetary values

## Installation

Add the following to your build.sbt:

```scala
libraryDependencies += "com.github.j5ik2o" %% "pekko-persistence-typed-fsm" % "0.1.0-SNAPSHOT"
```

## License

This library is licensed under the Apache License 2.0.

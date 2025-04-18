# pekko-persistence-effector

[![CI](https://github.com/j5ik2o/pekko-persistence-effector/workflows/CI/badge.svg)](https://github.com/j5ik2o/pekko-persistence-effector/actions?query=workflow%3ACI)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Tokei](https://tokei.rs/b1/github/j5ik2o/pekko-persistence-dynamodb)](https://github.com/XAMPPRocky/tokei)

A library for efficient implementation of event sourcing and state transitions with Apache Pekko.

*Read this in other languages: [日本語](README.ja.md)*

## Overview

`pekko-persistence-effector` is a library that improves the implementation of event sourcing patterns using Apache Pekko. It eliminates the constraints of traditional Pekko Persistence Typed and enables event sourcing with a more intuitive actor programming style.

### Key Features

- **Traditional Actor Programming Style**: Enables event sourcing while maintaining the usual Behavior-based actor programming style
- **Single Execution of Domain Logic**: Eliminates the problem of double execution of domain logic in command handlers
- **High Compatibility with DDD**: Supports seamless integration with domain objects
- **Incremental Implementation**: Start with in-memory mode during development, then migrate to persistence later
- **Type Safety**: Type-safe design utilizing Scala 3's type system

## Background: Why This Library is Needed

Traditional Pekko Persistence Typed has the following issues:

1. **Inconsistency with Traditional Actor Programming Style**: 
   - Forces you to use EventSourcedBehavior patterns that differ from regular Behavior-based programming
   - Makes learning curve steeper and implementation more difficult

2. **Reduced Maintainability with Complex State Transitions**: 
   - Command handlers become complex with multiple match/case statements
   - Cannot split handlers based on state, leading to decreased code readability

3. **Double Execution of Domain Logic**: 
   - Domain logic is executed in both command handlers and event handlers
   - Command handlers cannot use state updated by domain logic, making integration with domain objects awkward

This library solves these problems by implementing "Persistent Actor as a child actor of the aggregate actor." The implementation specifically uses Untyped PersistentActor internally (rather than EventSourcedBehavior) to avoid the double execution of domain logic.

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

### InMemoryEffector

An implementation of PersistenceEffector that stores events and snapshots in memory.

```scala
final class InMemoryEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
) extends PersistenceEffector[S, E, M]
```

- **Key features**:
  - In-memory storage of events and snapshots
  - Events and snapshots are stored in memory, allowing for fast development without database setup
  - State restoration from saved events during actor initialization
  - Immediate persistence without the latency of actual database operations
  - Perfect for development, testing, and prototyping phases
  - Provides a seamless path to migrate to actual persistence later

### PersistenceEffectorConfig

A case class that defines the configuration for PersistenceEffector.

```scala
final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  messageConverter: MessageConverter[S, E, M],
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
  retentionCriteria: Option[RetentionCriteria] = None,
  backoffConfig: Option[BackoffConfig] = None,
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

### Result

A case class that encapsulates the result of domain operations, containing the new state and event.

```scala
final case class Result[S, E](
  bankAccount: S,
  event: E,
)
```

The Result class provides several key benefits:
- **Type Safety**: Explicitly captures the relationship between new state and corresponding event
- **Readability**: More meaningful than tuples, clearly showing the purpose of each value
- **Maintainability**: Pattern matching becomes more explicit and easier to change
- **Domain Modeling**: Standardizes the return values from domain logic

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
          result => State.Created(id, result.bankAccount),
        )
    case (State.Created(id, bankAccount), BankAccountEvent.CashWithdrew(_, amount, _)) =>
      bankAccount
        .subtract(amount)
        .fold(
          error => throw new IllegalStateException(s"Failed to apply event: $error"),
          result => State.Created(id, result.bankAccount),
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
    case BankAccountCommand.DepositCash(aggregateId, amount, replyTo) =>
      // Execute domain logic
      state.bankAccount
        .add(amount)
        .fold(
          error => {
            replyTo ! DepositCashReply.Failed(aggregateId, error)
            Behaviors.same
          },
          { case Result(newBankAccount, event) =>
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

## When to Use This Library

This library is particularly well-suited for:

- When you want to implement incrementally, starting without persistence and later adding it
- When dealing with complex state transitions that would be difficult to maintain with traditional Pekko Persistence Typed
- When implementing DDD-oriented designs where you want to separate domain logic from actors
- When you need a more natural actor programming style for event sourcing applications

## Installation

Add the following to your build.sbt:

```scala
resolvers += "GitHub Packages" at
  "https://maven.pkg.github.com/j5ik2o/pekko-persistence-effector"
libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "pekko-persistence-effector" % "..."
)
```

## License

This library is licensed under the Apache License 2.0.

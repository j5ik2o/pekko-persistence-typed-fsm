# pekko-persistence-effector

[![CI](https://github.com/j5ik2o/pekko-persistence-effector/workflows/CI/badge.svg)](https://github.com/j5ik2o/pekko-persistence-effector/actions?query=workflow%3ACI)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Tokei](https://tokei.rs/b1/github/j5ik2o/pekko-persistence-dynamodb)](https://github.com/XAMPPRocky/tokei)

A library for efficient implementation of event sourcing and state transitions with Apache Pekko.

*Read this in other languages: [日本語](README.ja.md)*

## Overview

`pekko-persistence-effector` is a library that improves the implementation of event sourcing patterns using Apache Pekko. It eliminates the constraints of traditional Pekko Persistence Typed and enables event sourcing with a more intuitive actor programming style, supporting both Scala and Java DSLs.

### Key Features

- **Traditional Actor Programming Style**: Enables event sourcing while maintaining the usual Behavior-based actor programming style (Scala & Java).
- **Single Execution of Domain Logic**: Eliminates the problem of double execution of domain logic in command handlers.
- **High Compatibility with DDD**: Supports seamless integration with domain objects.
- **Incremental Implementation**: Start with in-memory mode during development, then migrate to persistence later.
- **Type Safety**: Type-safe design utilizing Scala 3's type system (Scala DSL).
- **Enhanced Error Handling**: Includes configurable retry mechanisms for persistence operations, improving resilience against transient failures.

## Background: Why This Library is Needed

Traditional Pekko Persistence Typed has the following issues:

1. **Inconsistency with Traditional Actor Programming Style**:
   - Forces you to use EventSourcedBehavior patterns that differ from regular Behavior-based programming.
   - Makes learning curve steeper and implementation more difficult.

2. **Reduced Maintainability with Complex State Transitions**:
   - Command handlers become complex with multiple match/case statements.
   - Cannot split handlers based on state, leading to decreased code readability.

3. **Double Execution of Domain Logic**:
   - Domain logic is executed in both command handlers and event handlers.
   - Command handlers cannot use state updated by domain logic, making integration with domain objects awkward.

This library solves these problems by implementing "Persistent Actor as a child actor of the aggregate actor." The implementation specifically uses Untyped PersistentActor internally (rather than EventSourcedBehavior) to avoid the double execution of domain logic.

## Main Components

### PersistenceEffector

A core trait (Scala) / interface (Java) that provides event persistence functionality.

```scala
// Scala DSL
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
  // ... includes methods for retry logic
}
```

```java
// Java DSL
public interface PersistenceEffector<State, Event, Message> {
    Behavior<Message> persistEvent(Event event, Function<Event, Behavior<Message>> onPersisted);
    Behavior<Message> persistEvents(List<Event> events, Function<List<Event>, Behavior<Message>> onPersisted);
    Behavior<Message> persistSnapshot(State snapshot, Function<State, Behavior<Message>> onPersisted);
    // ... includes methods for retry logic
}
```

### PersistenceEffectorConfig

A case class (Scala) / class (Java) that defines the configuration for PersistenceEffector.

```scala
// Scala DSL
final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  messageConverter: MessageConverter[S, E, M],
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
  retentionCriteria: Option[RetentionCriteria] = None,
  backoffConfig: Option[BackoffConfig] = None, // For PersistenceStoreActor restart
  persistTimeout: FiniteDuration = 30.seconds, // Timeout for each persist attempt
  maxRetries: Int = 3 // Max retries for persist operations
)
```

```java
// Java DSL
public class PersistenceEffectorConfig<State, Event, Message> {
    private final String persistenceId;
    private final State initialState;
    private final BiFunction<State, Event, State> applyEvent;
    private final MessageConverter<State, Event, Message> messageConverter;
    private final PersistenceMode persistenceMode;
    private final int stashSize;
    private final Optional<SnapshotCriteria<State, Event>> snapshotCriteria;
    private final Optional<RetentionCriteria> retentionCriteria;
    private final Optional<BackoffConfig> backoffConfig; // For PersistenceStoreActor restart
    private final Duration persistTimeout; // Timeout for each persist attempt
    private final int maxRetries; // Max retries for persist operations
    // Constructor and builder...
}
```

### MessageConverter

A trait (Scala) / interface (Java) that defines the conversions between state (S), event (E), and message (M).

```scala
// Scala DSL
trait MessageConverter[S, E, M <: Matchable] {
  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedState(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]
  def wrapPersistFailedAfterRetries(command: Any, cause: Throwable): M & PersistFailedAfterRetries[M]
  // ...
}
```
```java
// Java DSL
public interface MessageConverter<State, Event, Message> {
    Message wrapPersistedEvents(List<Event> events);
    Message wrapPersistedState(State state);
    Message wrapRecoveredState(State state);
    Message wrapPersistFailedAfterRetries(Object command, Throwable cause);
    // Unwrappers...
}
```

### Result

A case class (Scala) / class (Java) that encapsulates the result of domain operations, containing the new state and event.

```scala
// Scala DSL
final case class Result[S, E](
  newState: S, // Renamed for clarity
  event: E,
)
```
```java
// Java DSL
public class Result<State, Event> {
    private final State newState;
    private final Event event;
    // Constructor, getters...
}
```

The Result class provides several key benefits:
- **Type Safety**: Explicitly captures the relationship between new state and corresponding event
- **Readability**: More meaningful than tuples, clearly showing the purpose of each value
- **Maintainability**: Pattern matching becomes more explicit and easier to change
- **Domain Modeling**: Standardizes the return values from domain logic

## Usage Examples

### BankAccount Example (Scala DSL)

```scala
// 1. Define state and state transition function
enum State {
  def aggregateId: BankAccountId
  case NotCreated(aggregateId: BankAccountId)
  case Created(aggregateId: BankAccountId, bankAccount: BankAccount)

  def applyEvent(event: BankAccountEvent): State = (this, event) match {
    case (State.NotCreated(aggregateId), BankAccountEvent.Created(id, _)) =>
      Created(id, BankAccount(id))
    // ... other transitions
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
  persistenceMode = PersistenceMode.Persisted, // Or InMemory
  persistTimeout = 5.seconds,
  maxRetries = 2
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
    effector.persistEvent(event) { _ => // Callback executed after successful persistence
      cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
      handleCreated(State.Created(state.aggregateId, bankAccount), effector)
    } // If persistence fails after retries, a PersistFailedAfterRetries message is sent via MessageConverter
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
          error => { // Domain validation failed
            replyTo ! DepositCashReply.Failed(aggregateId, error)
            Behaviors.same
          },
          { case Result(newBankAccount, event) => // Domain logic succeeded
            // Persist the event (with retries on failure)
            effector.persistEvent(event) { _ => // Callback on success
              replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
              // Update actor state and behavior
              handleCreated(state.copy(bankAccount = newBankAccount), effector)
            }
          },
        )
    // Handle PersistFailedAfterRetries message if needed
    case BankAccountCommand.WrappedPersistFailed(cmd, cause) =>
       // Log error, notify original sender, potentially stop actor etc.
       Behaviors.same
  }
```

*(See Java DSL examples in the `src/test/java` directory)*

## Example Code Files

For more detailed implementation examples, see the following files:

**Scala DSL:**
- [BankAccountAggregate](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountAggregate.scala)
- [BankAccount](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccount.scala)
- [BankAccountCommand](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountCommand.scala)
- [BankAccountEvent](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountEvent.scala)

**Java DSL:**
- [BankAccountAggregate](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountAggregate.java)
- [BankAccount](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccount.java)
- [BankAccountCommand](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountCommand.java)
- [BankAccountEvent](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountEvent.java)

## When to Use This Library

This library is particularly well-suited for:

- When you want to implement incrementally, starting without persistence and later adding it.
- When dealing with complex state transitions that would be difficult to maintain with traditional Pekko Persistence Typed.
- When implementing DDD-oriented designs where you want to separate domain logic from actors.
- When you need a more natural actor programming style for event sourcing applications (Scala or Java).
- When resilience against temporary persistence failures is required.

## Installation

Note: This library does not depend on `pekko-persistence-typed`. You can use this library even without adding `pekko-persistence-typed` as a dependency.

Add the following to your `build.sbt`:

```scala
resolvers += "GitHub Packages" at
  "https://maven.pkg.github.com/j5ik2o/pekko-persistence-effector"

libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "pekko-persistence-effector" % "<latest_version>"
)
```

Or for Maven (`pom.xml`):
```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/j5ik2o/pekko-persistence-effector</url>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.j5ik2o</groupId>
    <artifactId>pekko-persistence-effector_3</artifactId> <!-- Or _2.13 -->
    <version>LATEST</version> <!-- Replace with specific version -->
  </dependency>
</dependencies>
```
*(Remember to configure authentication for GitHub Packages)*

## License

This library is licensed under the Apache License 2.0.

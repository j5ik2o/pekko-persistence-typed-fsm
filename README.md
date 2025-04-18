# pekko-persistence-effector

[![CI](https://github.com/j5ik2o/pekko-persistence-effector/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/j5ik2o/pekko-persistence-effector/actions/workflows/ci.yml)
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

## Usage Examples

### BankAccount Example (Scala DSL)

Here's a complete example showing how to implement a bank account aggregate using pekko-persistence-effector:

```scala
// 1. Define domain model, commands, events, and replies using Scala 3 features

// Domain model
case class BankAccountId(value: String)
case class Money(amount: BigDecimal)
case class BankAccount(id: BankAccountId, balance: Money = Money(0)) {
  def deposit(amount: Money): Either[BankAccountError, (BankAccount, BankAccountEvent)] = {
    if (amount.amount <= 0) {
      Left(BankAccountError.InvalidAmount)
    } else {
      Right((copy(balance = Money(balance.amount + amount.amount)), 
             BankAccountEvent.Deposited(id, amount, Instant.now())))
    }
  }
                
  def withdraw(amount: Money): Either[BankAccountError, (BankAccount, BankAccountEvent)] = {
    if (amount.amount <= 0) {
      Left(BankAccountError.InvalidAmount)
    } else if (balance.amount < amount.amount) {
      Left(BankAccountError.InsufficientFunds)
    } else {
      Right((copy(balance = Money(balance.amount - amount.amount)),
             BankAccountEvent.Withdrawn(id, amount, Instant.now())))
    }
  }
}

// Commands
enum BankAccountCommand {
  case Create(id: BankAccountId, replyTo: ActorRef[CreateReply])
  case Deposit(id: BankAccountId, amount: Money, replyTo: ActorRef[DepositReply])
  case Withdraw(id: BankAccountId, amount: Money, replyTo: ActorRef[WithdrawReply])
  case GetBalance(id: BankAccountId, replyTo: ActorRef[GetBalanceReply])
  
  // Internal messages for PersistenceEffector communication
  private case WrappedPersistEvent(event: BankAccountEvent)
  private case WrappedRecoveredState(state: BankAccountAggregate.State)
  private case WrappedPersistFailed(command: Any, cause: Throwable)
}

// MessageConverter implementation
object BankAccountCommand {
  val messageConverter: MessageConverter[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand] = 
    new MessageConverter[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand] {
      override def wrapPersistedEvents(events: Seq[BankAccountEvent]): BankAccountCommand & PersistedEvent[BankAccountEvent, BankAccountCommand] = {
        BankAccountCommand.WrappedPersistEvent(events.head).asInstanceOf[BankAccountCommand & PersistedEvent[BankAccountEvent, BankAccountCommand]]
      }
        
      override def wrapRecoveredState(state: BankAccountAggregate.State): BankAccountCommand & RecoveredState[BankAccountAggregate.State, BankAccountCommand] = {
        BankAccountCommand.WrappedRecoveredState(state).asInstanceOf[BankAccountCommand & RecoveredState[BankAccountAggregate.State, BankAccountCommand]]
      }
        
      override def wrapPersistFailedAfterRetries(command: Any, cause: Throwable): BankAccountCommand & PersistFailedAfterRetries[BankAccountCommand] = {
        BankAccountCommand.WrappedPersistFailed(command, cause).asInstanceOf[BankAccountCommand & PersistFailedAfterRetries[BankAccountCommand]]
      }
        
      // Other required methods...
    }
}

// Events
enum BankAccountEvent {
  def id: BankAccountId
  def occurredAt: Instant
  
  case Created(id: BankAccountId, occurredAt: Instant) extends BankAccountEvent
  case Deposited(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
  case Withdrawn(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
}

// Replies
enum CreateReply {
  case Succeeded(id: BankAccountId)
  case Failed(id: BankAccountId, error: BankAccountError)
}

enum DepositReply {
  case Succeeded(id: BankAccountId, amount: Money)
  case Failed(id: BankAccountId, error: BankAccountError)
}

// Error types
enum BankAccountError {
  case InvalidAmount
  case InsufficientFunds
  case AlreadyExists
  case NotFound
}

// 2. Define the aggregate actor
object BankAccountAggregate {
  // State definition using enum
  enum State {
    def id: BankAccountId
    
    case NotCreated(id: BankAccountId) extends State
    case Active(id: BankAccountId, account: BankAccount) extends State
    
    // Event application logic
    def applyEvent(event: BankAccountEvent): State = (this, event) match {
      case (NotCreated(id), BankAccountEvent.Created(_, _)) =>
        Active(id, BankAccount(id))
        
      case (active: Active, evt: BankAccountEvent.Deposited) =>
        val newAccount = active.account.copy(
          balance = Money(active.account.balance.amount + evt.amount.amount)
        )
        active.copy(account = newAccount)
        
      case (active: Active, evt: BankAccountEvent.Withdrawn) =>
        val newAccount = active.account.copy(
          balance = Money(active.account.balance.amount - evt.amount.amount)
        )
        active.copy(account = newAccount)
        
      case _ =>
        throw IllegalStateException(s"Invalid state transition: $this -> $event")
    }
  }
  
  // Actor factory
  def apply(id: BankAccountId): Behavior[BankAccountCommand] = {
    Behaviors.setup { context =>
      // Create PersistenceEffector configuration
      val config = PersistenceEffectorConfig[State, BankAccountEvent, BankAccountCommand](
        persistenceId = s"bank-account-${id.value}",
        initialState = State.NotCreated(id),
        applyEvent = (state, event) => state.applyEvent(event),
        messageConverter = BankAccountCommand.messageConverter,
        persistenceMode = PersistenceMode.Persisted, // Or InMemory for development
        stashSize = 100,
        persistTimeout = 5.seconds,
        maxRetries = 3
      )
      
      // Create PersistenceEffector
      PersistenceEffector.fromConfig(config) {
        case (state: State.NotCreated, effector) => handleNotCreated(state, effector)
        case (state: State.Active, effector) => handleActive(state, effector)
      }
    }
  }
  
  // Handler for NotCreated state
  private def handleNotCreated(
    state: State.NotCreated,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Create =>
        // Create a new account and generate event
        val event = BankAccountEvent.Created(cmd.id, Instant.now())
        
        // Persist the event
        effector.persistEvent(event) { _ =>
          // After successful persistence, reply and change behavior
          cmd.replyTo ! CreateReply.Succeeded(cmd.id)
          handleActive(State.Active(cmd.id, BankAccount(cmd.id)), effector)
        }
    }
  }
  
  // Handler for Active state
  private def handleActive(
    state: State.Active,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Deposit =>
        // Execute domain logic
        state.account.deposit(cmd.amount) match {
          case Left(error) =>
            // Domain validation failed
            cmd.replyTo ! DepositReply.Failed(cmd.id, error)
            Behaviors.same
            
          case Right((newAccount, event)) =>
            // Persist the event
            effector.persistEvent(event) { _ =>
              // After successful persistence, reply and update state
              cmd.replyTo ! DepositReply.Succeeded(cmd.id, cmd.amount)
              handleActive(state.copy(account = newAccount), effector)
            }
        }
        
      case cmd: BankAccountCommand.Withdraw =>
        // Similar implementation to Deposit...
        
      case cmd: BankAccountCommand.GetBalance =>
        // Read-only operation, no persistence needed
        cmd.replyTo ! GetBalanceReply.Success(cmd.id, state.account.balance)
        Behaviors.same
    }
  }
}
}
```

This example demonstrates:
1. Domain model with validation logic
2. Command, event, and reply message definitions
3. State definition with event application logic
4. PersistenceEffector configuration and creation
5. State-specific message handlers
6. Error handling for both domain validation and persistence failures

*(See Java DSL examples in the `src/test/java` directory)*

## Example Code Files

For more detailed implementation examples, see the following files:

**Scala DSL:**
- Aggregate: [BankAccountAggregate.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountAggregate.scala)
- Domain Model: [BankAccount.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccount.scala)
- Commands: [BankAccountCommand.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountCommand.scala)
- Events: [BankAccountEvent.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountEvent.scala)

**Java DSL:**
- Aggregate: [BankAccountAggregate.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountAggregate.java)
- Domain Model: [BankAccount.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccount.java)
- Commands: [BankAccountCommand.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountCommand.java)
- Events: [BankAccountEvent.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountEvent.java)

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

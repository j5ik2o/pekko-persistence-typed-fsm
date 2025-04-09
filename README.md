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

This library solves these problems by implementing "EventSourcedBehavior as a child actor of the aggregate actor."

## Main Components

### Effector

A core trait that provides event persistence functionality.

```scala
trait Effector[S, E, M] {
  def persist(event: E)(onPersisted: (Option[S], E) => Behavior[M]): Behavior[M]
  def persistAll(events: Seq[E])(onPersisted: (Option[S], Seq[E]) => Behavior[M]): Behavior[M]
}
```

### EffectorConfig

A case class that defines the configuration for Effector.

```scala
final case class EffectorConfig[S, E, M](
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
  def wrapPersisted(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapRecovered(state: S): M & RecoveredState[S, M]
  // ...
}
```

## Usage Examples

### Basic Usage

```scala
// 1. Define state and state transition function
enum State {
  case NotCreated(aggregateId: EntityId)
  case Created(aggregateId: EntityId, entity: Entity)
  
  def applyEvent(event: Event): State = // Implementation of state transition
}

// 2. Configure EffectorConfig
val config = EffectorConfig[State, Event, Command](
  persistenceId = entityId.toString,
  initialState = State.NotCreated(entityId),
  applyEvent = (state, event) => state.applyEvent(event),
  messageConverter = Command.messageConverter  // Or specify individual conversion functions
)

// 3. Create an actor using Effector
Behaviors.setup[Command] { implicit ctx =>
  Effector.create[State, Event, Command](config) {
    case (state: State.NotCreated, effector) =>
      handleNotCreated(state, effector)
    case (state: State.Created, effector) =>
      handleCreated(state, effector)
  }
}

// 4. Implement handlers according to state
private def handleCreated(state: State.Created, effector: Effector[State, Event, Command]): Behavior[Command] =
  Behaviors.receiveMessagePartial {
    case Command.DoSomething(id, param, replyTo) =>
      // Execute domain logic
      state.entity.doSomething(param) match {
        case Right((newEntity, event)) =>
          // Persist the event
          effector.persist(event) { (newState, _) =>
            replyTo ! Reply.Success(id)
            // Update with new state
            handleCreated(newState.asInstanceOf[State.Created], effector)
          }
        case Left(error) =>
          replyTo ! Reply.Failed(id, error)
          Behaviors.same
      }
  }
```

For more detailed implementation examples, see [BankAccountAggregate](src/test/scala/example/BankAccountAggregate.scala).

## Installation

Add the following to your build.sbt:

```scala
libraryDependencies += "com.github.j5ik2o" %% "pekko-persistence-typed-fsm" % "0.1.0-SNAPSHOT"
```

## License

This library is licensed under the Apache License 2.0.

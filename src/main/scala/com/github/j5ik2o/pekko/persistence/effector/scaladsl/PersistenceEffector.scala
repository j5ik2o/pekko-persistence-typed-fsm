package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.PersistenceStoreProtocol.*
import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.DefaultPersistenceEffector
import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.{
  InMemoryEffector,
  PersistenceStoreActor,
}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.compiletime.asMatchable

/**
 * Trait defining the persistence operations for event sourcing.
 * This trait provides methods to persist events and snapshots, and to manage the lifecycle of persisted data.
 *
 * @tparam S Type of state
 * @tparam E Type of event
 * @tparam M Type of message
 */
trait PersistenceEffector[S, E, M] {
  /**
   * Persist a single event.
   *
   * @param event Event to persist
   * @param onPersisted Callback function to execute after the event is persisted
   * @return The behavior returned by the callback
   */
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  
  /**
   * Persist multiple events.
   *
   * @param events Events to persist
   * @param onPersisted Callback function to execute after the events are persisted
   * @return The behavior returned by the callback
   */
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]

  /**
   * Persist a snapshot.
   *
   * @param snapshot Snapshot to persist
   * @param onPersisted Callback function to execute after the snapshot is persisted
   * @return The behavior returned by the callback
   */
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M] =
    persistSnapshot(snapshot, force = false)(onPersisted)

  /**
   * Persist a snapshot with force option.
   *
   * @param snapshot Snapshot to persist
   * @param force If true, forces snapshot persistence regardless of snapshot criteria
   * @param onPersisted Callback function to execute after the snapshot is persisted
   * @return The behavior returned by the callback
   */
  def persistSnapshot(snapshot: S, force: Boolean)(onPersisted: S => Behavior[M]): Behavior[M]

  /**
   * Persist an event and evaluate snapshot criteria with the current state (for backward compatibility).
   *
   * @param event Event to persist
   * @param snapshot Current state
   * @param onPersisted Callback function to execute after the event is persisted
   * @return The behavior returned by the callback
   */
  def persistEventWithSnapshot(event: E, snapshot: S)(onPersisted: E => Behavior[M]): Behavior[M] =
    persistEventWithSnapshot(event, snapshot, forceSnapshot = false)(onPersisted)

  /**
   * Persist an event and evaluate snapshot criteria with the current state.
   *
   * @param event Event to persist
   * @param snapshot Current state
   * @param forceSnapshot If true, forces snapshot persistence regardless of snapshot criteria
   * @param onPersisted Callback function to execute after the event is persisted
   * @return The behavior returned by the callback
   */
  def persistEventWithSnapshot(event: E, snapshot: S, forceSnapshot: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M]

  /**
   * Persist multiple events and evaluate snapshot criteria with the current state (for backward compatibility).
   *
   * @param events Sequence of events to persist
   * @param snapshot Current state
   * @param onPersisted Callback function to execute after the events are persisted
   * @return The behavior returned by the callback
   */
  def persistEventsWithSnapshot(events: Seq[E], snapshot: S)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] =
    persistEventsWithSnapshot(events, snapshot, forceSnapshot = false)(onPersisted)

  /**
   * Persist multiple events and evaluate snapshot criteria with the current state.
   *
   * @param events Sequence of events to persist
   * @param snapshot Current state
   * @param forceSnapshot If true, forces snapshot persistence regardless of snapshot criteria
   * @param onPersisted Callback function to execute after the events are persisted
   * @return The behavior returned by the callback
   */
  def persistEventsWithSnapshot(events: Seq[E], snapshot: S, forceSnapshot: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M]
}

object PersistenceEffector {
  // Message for handling recovery completion internally
  private case class RecoveryCompletedInternal[S](state: S, sequenceNr: Long)

  /**
   * Create a PersistenceEffector in persisted mode.
   *
   * @param persistenceId Persistence ID
   * @param initialState Initial state
   * @param applyEvent Function to apply events to state
   * @param messageConverter Message converter
   * @param stashSize Size of the stash buffer
   * @param snapshotCriteria Snapshot criteria
   * @param retentionCriteria Retention criteria
   * @param backoffConfig Backoff configuration
   * @param onReady Callback when the effector is ready
   * @param context Actor context
   * @return Actor behavior
   */
  def persisted[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
    retentionCriteria: Option[RetentionCriteria] = None,
    backoffConfig: Option[BackoffConfig] = None,
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] = {
    val config = PersistenceEffectorConfig.create(
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      persistenceMode = PersistenceMode.Persisted,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
      backoffConfig = backoffConfig,
      messageConverter = messageConverter,
    )
    build(config)(onReady)
  }

  /**
   * Create a PersistenceEffector in in-memory mode.
   *
   * @param persistenceId Persistence ID
   * @param initialState Initial state
   * @param applyEvent Function to apply events to state
   * @param messageConverter Message converter
   * @param stashSize Size of the stash buffer
   * @param snapshotCriteria Snapshot criteria
   * @param retentionCriteria Retention criteria
   * @param backoffConfig Backoff configuration
   * @param onReady Callback when the effector is ready
   * @param context Actor context
   * @return Actor behavior
   */
  def ephemeral[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
    retentionCriteria: Option[RetentionCriteria] = None,
    backoffConfig: Option[BackoffConfig] = None,
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] = {
    val config = PersistenceEffectorConfig.create(
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      persistenceMode = PersistenceMode.Ephemeral,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
      backoffConfig = backoffConfig,
      messageConverter = messageConverter,
    )
    build(config)(onReady)
  }

  /**
   * Create a PersistenceEffector from a configuration.
   *
   * @param config Configuration
   * @param onReady Callback when the effector is ready
   * @param context Actor context
   * @return Actor behavior
   */
  def fromConfig[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] =
    build(config)(onReady)

  /**
   * Build a PersistenceEffector based on configuration.
   *
   * @param config Configuration
   * @param onReady Callback when the effector is ready
   * @param context Actor context
   * @return Actor behavior
   */
  private def build[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] =
    config.persistenceMode match {
      case PersistenceMode.Persisted =>
        // Existing persistence implementation
        import config.*
        // Fix recoveryAdapter: Convert from RecoveryDone to RecoveryCompletedInternal
        val recoveryAdapter = context.messageAdapter[RecoveryDone[S]] { rd =>
          // Wrap both state and sequenceNr
          // Using asInstanceOf because we need to cast to M type
          RecoveryCompletedInternal(rd.state, rd.sequenceNr).asInstanceOf[M]
        }

        val persistenceRef = spawnEventStoreActor(
          context,
          persistenceId,
          initialState,
          applyEvent,
          recoveryAdapter,
          backoffConfig,
        )

        val adapter = context.messageAdapter[PersistenceReply[S, E]] {
          // Event persistence
          case PersistSingleEventSucceeded(event) => wrapPersistedEvents(Seq(event))
          case PersistMultipleEventsSucceeded(events) => wrapPersistedEvents(events)
          // Snapshot persistence
          case PersistSnapshotSucceeded(snapshot) => wrapPersistedSnapshot(snapshot)
          case PersistSnapshotFailed(snapshot, cause) =>
            throw new IllegalStateException("Failed to persist snapshot", cause)
          // Snapshot deletion
          case DeleteSnapshotsSucceeded(maxSequenceNumber) => wrapDeleteSnapshots(maxSequenceNumber)
          case DeleteSnapshotsFailed(maxSequenceNumber, cause) =>
            throw new IllegalStateException("Failed to delete snapshots", cause)
        }

        def awaitRecovery(): Behavior[M] =
          Behaviors.withStash(config.stashSize) { stashBuffer =>
            // Use receiveMessagePartial to improve type safety
            Behaviors.receiveMessagePartial { msg =>
              // Direct matching with RecoveryCompletedInternal type (requires @unchecked)
              msg.asMatchable match {
                case msg: RecoveryCompletedInternal[?] =>
                  val state = msg.asInstanceOf[RecoveryCompletedInternal[S]].state
                  val sequenceNr =
                    msg.asInstanceOf[RecoveryCompletedInternal[S]].sequenceNr // Extract sequenceNr
                  context.log.debug(
                    "Recovery completed. State = {}, SequenceNr = {}",
                    state,
                    sequenceNr,
                  ) // Use context directly
                  val effector = new DefaultPersistenceEffector[S, E, M](
                    context, // Use context directly
                    stashBuffer,
                    config,
                    persistenceRef,
                    adapter,
                    sequenceNr, // Pass sequenceNr to DefaultPersistenceEffector
                  )
                  stashBuffer.unstashAll(onReady(state, effector))
                case msg => // Stash other messages
                  context.log.debug("Stashing message during recovery: {}", msg) // Use context directly
                  stashBuffer.stash(msg)
                  Behaviors.same
              }
            }
          }

        awaitRecovery()

      case PersistenceMode.Ephemeral =>
        // In-memory implementation
        Behaviors.withStash(config.stashSize) { stashBuffer =>
          val effector = new InMemoryEffector[S, E, M](
            context,
            stashBuffer,
            config,
          )
          // Use initial state directly because it's in-memory
          onReady(effector.getState, effector)
        }
    }

  private def spawnEventStoreActor[M, E, S](
    context: ActorContext[M],
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    recoveryAdapter: ActorRef[RecoveryDone[S]],
    backoffConfig: Option[BackoffConfig]) = {
    import org.apache.pekko.actor.typed.scaladsl.adapter.*
    context
      .actorOf(
        PersistenceStoreActor.props(
          persistenceId,
          initialState,
          applyEvent,
          recoveryAdapter,
          backoffConfig,
        ),
        s"effector-$persistenceId",
      )
      .toTyped
  }

}

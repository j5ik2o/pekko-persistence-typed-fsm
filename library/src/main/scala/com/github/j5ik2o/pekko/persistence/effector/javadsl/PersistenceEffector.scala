package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.internal.javaimpl.PersistenceEffectorWrapper
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceEffector as ScalaPersistenceEffector
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import java.util
import java.util.function.{BiFunction, Function}
import java.util.{function, Optional}

/**
 * Java API for PersistenceEffector.
 *
 * @param S
 *   State type
 * @param E
 *   Event type
 * @param M
 *   Message type
 */
trait PersistenceEffector[S, E, M] {

  /**
   * Persist a single event.
   *
   * @param event
   *   event to persist
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEvent(event: E, onPersisted: Function[E, Behavior[M]]): Behavior[M]

  /**
   * Persist multiple events.
   *
   * @param events
   *   events to persist
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEvents(
    events: util.List[E],
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M]

  /**
   * Persist a snapshot.
   *
   * @param snapshot
   *   snapshot to persist
   * @param onPersisted
   *   callback to be called after the snapshot is persisted
   * @return
   *   new behavior
   */
  def persistSnapshot(snapshot: S, onPersisted: Function[S, Behavior[M]]): Behavior[M] =
    persistSnapshot(snapshot, false, onPersisted)

  /**
   * Persist a snapshot with the force option.
   *
   * @param snapshot
   *   snapshot to persist
   * @param force
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after the snapshot is persisted
   * @return
   *   new behavior
   */
  def persistSnapshot(
    snapshot: S,
    force: Boolean,
    onPersisted: Function[S, Behavior[M]]): Behavior[M]

  /**
   * Persist event with snapshot.
   *
   * @param event
   *   event to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEventWithSnapshot(
    event: E,
    snapshot: S,
    onPersisted: Function[E, Behavior[M]]): Behavior[M] =
    persistEventWithSnapshot(event, snapshot, false, onPersisted)

  /**
   * Persist event with a snapshot and force option.
   *
   * @param event
   *   event to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param forceSnapshot
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEventWithSnapshot(
    event: E,
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: Function[E, Behavior[M]]): Behavior[M]

  /**
   * Persist multiple events with a snapshot.
   *
   * @param events
   *   events to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEventsWithSnapshot(
    events: util.List[E],
    snapshot: S,
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M] =
    persistEventsWithSnapshot(events, snapshot, false, onPersisted)

  /**
   * Persist multiple events with snapshot and force option.
   *
   * @param events
   *   events to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param forceSnapshot
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEventsWithSnapshot(
    events: util.List[E],
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M]
}

object PersistenceEffector {

  /**
   * Create PersistenceEffector in persistence mode
   *
   * @param persistenceId
   *   Persistence ID
   * @param initialState
   *   Initial state
   * @param applyEvent
   *   Event application function
   * @param messageConverter
   *   Message converter
   * @param stashSize
   *   Stash size
   * @param snapshotCriteria
   *   Snapshot criteria
   * @param retentionCriteria
   *   Retention criteria
   * @param backoffConfig
   *   Backoff configuration
   * @param onReady
   *   Callback when ready
   * @return
   *   Behavior
   */
  def persisted[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: BiFunction[S, E, S],
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Optional[SnapshotCriteria[S, E]] = Optional.empty(),
    retentionCriteria: Optional[RetentionCriteria] = Optional.empty(),
    backoffConfig: Optional[BackoffConfig] = Optional.empty(),
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] = {
    val config = PersistenceEffectorConfig.create[S, E, M](
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      messageConverter = messageConverter,
      persistenceMode = PersistenceMode.PERSISTENCE,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
      backoffConfig = backoffConfig,
    )
    build(config, onReady)
  }

  /**
   * Create PersistenceEffector in in-memory mode
   *
   * @param persistenceId
   *   Persistence ID
   * @param initialState
   *   Initial state
   * @param applyEvent
   *   Event application function
   * @param messageConverter
   *   Message converter
   * @param stashSize
   *   Stash size
   * @param snapshotCriteria
   *   Snapshot criteria
   * @param retentionCriteria
   *   Retention criteria
   * @param backoffConfig
   *   Backoff configuration
   * @param onReady
   *   Callback when ready
   * @return
   *   Behavior
   */
  def ephemeral[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: BiFunction[S, E, S],
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Optional[SnapshotCriteria[S, E]] = Optional.empty(),
    retentionCriteria: Optional[RetentionCriteria] = Optional.empty(),
    backoffConfig: Optional[BackoffConfig] = Optional.empty(),
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] = {
    val config = PersistenceEffectorConfig.create[S, E, M](
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      messageConverter = messageConverter,
      persistenceMode = PersistenceMode.EPHEMERAL,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
      backoffConfig = backoffConfig,
    )
    build(config, onReady)
  }

  /**
   * Create PersistenceEffector with a specified configuration
   *
   * @param config
   *   Configuration
   * @param onReady
   *   Callback when ready
   * @return
   *   Behavior
   */
  def fromConfig[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] =
    build(config, onReady)

  /**
   * Build PersistenceEffector based on configuration
   *
   * @param config
   *   Configuration
   * @param onReady
   *   Callback when ready
   * @return
   *   Behavior
   */
  private def build[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] =
    Behaviors.setup { ctx =>
      // Call ScalaDSL version of PersistenceEffector
      ScalaPersistenceEffector.fromConfig(config.toScala) { case (state, scalaEffector) =>
        // Wrap with PersistenceEffectorWrapper for JavaDSL
        val javaEffector = PersistenceEffectorWrapper.create(scalaEffector)
        // Call onReady callback
        onReady.apply(state, javaEffector)
      }(using ctx)
    }

}

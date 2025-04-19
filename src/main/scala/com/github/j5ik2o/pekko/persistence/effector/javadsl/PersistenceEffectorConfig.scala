package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffectorConfig as SPersistenceEffectorConfig,
  PersistenceMode as SPersistenceMode,
}

import java.util.Optional
import scala.jdk.OptionConverters.*

/**
 * Configuration for PersistenceEffector in Java API.
 * This trait defines all the settings needed to create and configure a PersistenceEffector.
 *
 * @tparam S Type of state
 * @tparam E Type of event
 * @tparam M Type of message
 */
trait PersistenceEffectorConfig[S, E, M] {
  /**
   * Get the persistence ID for this effector.
   * This ID is used to uniquely identify the persistence stream.
   *
   * @return Persistence ID string
   */
  def persistenceId: String
  
  /**
   * Get the initial state for the effector.
   * This state is used when no previous state exists.
   *
   * @return Initial state
   */
  def initialState: S
  
  /**
   * Get the function to apply events to state.
   * This function is used to evolve the state when events are applied.
   *
   * @return Function that takes a state and an event and returns a new state
   */
  def applyEvent: java.util.function.BiFunction[S, E, S]
  
  /**
   * Get the persistence mode.
   * Determines whether events are persisted to disk or kept in memory.
   *
   * @return Persistence mode (PERSISTENCE or EPHEMERAL)
   */
  def persistenceMode: PersistenceMode
  
  /**
   * Get the stash size for the effector.
   * Determines how many messages can be stashed during recovery.
   *
   * @return Maximum stash size
   */
  def stashSize: Int
  
  /**
   * Get the snapshot criteria.
   * Determines when snapshots should be taken.
   *
   * @return Optional snapshot criteria
   */
  def snapshotCriteria: Optional[SnapshotCriteria[S, E]]
  
  /**
   * Get the retention criteria.
   * Determines how many snapshots and events should be kept.
   *
   * @return Optional retention criteria
   */
  def retentionCriteria: Optional[RetentionCriteria]
  
  /**
   * Get the backoff configuration.
   * Determines how to handle failures with backoff.
   *
   * @return Optional backoff configuration
   */
  def backoffConfig: Optional[BackoffConfig]
  
  /**
   * Get the message converter.
   * Used to convert between domain events/states and messages.
   *
   * @return Message converter
   */
  def messageConverter: MessageConverter[S, E, M]

  /**
   * Get the function to wrap persisted events into a message.
   *
   * @return Function to wrap events
   */
  def wrapPersistedEvents: java.util.function.Function[java.util.List[E], M]
  
  /**
   * Get the function to wrap a persisted snapshot into a message.
   *
   * @return Function to wrap snapshot
   */
  def wrapPersistedSnapshot: java.util.function.Function[S, M]
  
  /**
   * Get the function to wrap a recovered state into a message.
   *
   * @return Function to wrap recovered state
   */
  def wrapRecoveredState: java.util.function.Function[S, M]
  
  /**
   * Get the function to wrap deleted snapshots information into a message.
   *
   * @return Function to wrap deleted snapshots information
   */
  def wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M]

  /**
   * Get the function to extract persisted events from a message.
   *
   * @return Function to extract events
   */
  def unwrapPersistedEvents: java.util.function.Function[M, Option[java.util.List[E]]]
  
  /**
   * Get the function to extract persisted snapshot from a message.
   *
   * @return Function to extract snapshot
   */
  def unwrapPersistedSnapshot: java.util.function.Function[M, Option[S]]
  
  /**
   * Get the function to extract recovered state from a message.
   *
   * @return Function to extract recovered state
   */
  def unwrapRecoveredState: java.util.function.Function[M, Option[S]]
  
  /**
   * Get the function to extract deleted snapshots information from a message.
   *
   * @return Function to extract deleted snapshots information
   */
  def unwrapDeleteSnapshots: java.util.function.Function[M, Option[java.lang.Long]]

  /**
   * Create a new configuration with the specified persistence mode.
   *
   * @param value Persistence mode to use
   * @return New configuration with updated persistence mode
   */
  def withPersistenceMode(value: PersistenceMode): PersistenceEffectorConfig[S, E, M]
  
  /**
   * Create a new configuration with the specified stash size.
   *
   * @param value Stash size to use
   * @return New configuration with updated stash size
   */
  def withStashSize(value: Int): PersistenceEffectorConfig[S, E, M]
  
  /**
   * Create a new configuration with the specified snapshot criteria.
   *
   * @param value Snapshot criteria to use
   * @return New configuration with updated snapshot criteria
   */
  def withSnapshotCriteria(value: SnapshotCriteria[S, E]): PersistenceEffectorConfig[S, E, M]
  
  /**
   * Create a new configuration with the specified retention criteria.
   *
   * @param value Retention criteria to use
   * @return New configuration with updated retention criteria
   */
  def withRetentionCriteria(value: RetentionCriteria): PersistenceEffectorConfig[S, E, M]
  
  /**
   * Create a new configuration with the specified backoff configuration.
   *
   * @param value Backoff configuration to use
   * @return New configuration with updated backoff configuration
   */
  def withBackoffConfig(value: BackoffConfig): PersistenceEffectorConfig[S, E, M]
  
  /**
   * Create a new configuration with the specified message converter.
   *
   * @param value Message converter to use
   * @return New configuration with updated message converter
   */
  def withMessageConverter(value: MessageConverter[S, E, M]): PersistenceEffectorConfig[S, E, M]

  /**
   * Convert this Java configuration to its Scala equivalent.
   *
   * @return Scala version of this configuration
   */
  def toScala: SPersistenceEffectorConfig[S, E, M]
}

/**
 * Companion object for PersistenceEffectorConfig.
 * Provides factory methods to create configurations.
 */
object PersistenceEffectorConfig {

  final case class Impl[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: java.util.function.BiFunction[S, E, S],
    persistenceMode: PersistenceMode,
    stashSize: Int,
    snapshotCriteria: Optional[SnapshotCriteria[S, E]],
    retentionCriteria: Optional[RetentionCriteria],
    backoffConfig: Optional[BackoffConfig],
    messageConverter: MessageConverter[S, E, M],
  ) extends PersistenceEffectorConfig[S, E, M] {
    override def toScala: SPersistenceEffectorConfig[S, E, M] = {
      val scalaPersistenceMode = persistenceMode match {
        case PersistenceMode.PERSISTENCE => SPersistenceMode.Persisted
        case PersistenceMode.EPHEMERAL => SPersistenceMode.Ephemeral
      }

      SPersistenceEffectorConfig.create(
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = (s: S, e: E) => applyEvent.apply(s, e),
        persistenceMode = scalaPersistenceMode,
        stashSize = stashSize,
        snapshotCriteria = snapshotCriteria.toScala.map(_.toScala),
        retentionCriteria = retentionCriteria.toScala.map(_.toScala),
        backoffConfig = backoffConfig.toScala.map(_.toScala),
        messageConverter = messageConverter.toScala,
      )
    }

    override def wrapPersistedEvents: java.util.function.Function[java.util.List[E], M] =
      messageConverter.wrapPersistedEvents

    override def wrapPersistedSnapshot: java.util.function.Function[S, M] =
      messageConverter.wrapPersistedSnapshot

    override def wrapRecoveredState: java.util.function.Function[S, M] =
      messageConverter.wrapRecoveredState

    override def wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M] =
      messageConverter.wrapDeleteSnapshots

    override def unwrapPersistedEvents: java.util.function.Function[M, Option[java.util.List[E]]] =
      messageConverter.unwrapPersistedEvents

    override def unwrapPersistedSnapshot: java.util.function.Function[M, Option[S]] =
      messageConverter.unwrapPersistedSnapshot

    override def unwrapRecoveredState: java.util.function.Function[M, Option[S]] =
      messageConverter.unwrapRecoveredState

    override def unwrapDeleteSnapshots: java.util.function.Function[M, Option[java.lang.Long]] =
      messageConverter.unwrapDeleteSnapshots

    override def withPersistenceMode(value: PersistenceMode): PersistenceEffectorConfig[S, E, M] =
      copy(persistenceMode = value)

    override def withStashSize(value: Int): PersistenceEffectorConfig[S, E, M] =
      copy(stashSize = value)

    override def withSnapshotCriteria(
      value: SnapshotCriteria[S, E]): PersistenceEffectorConfig[S, E, M] =
      copy(snapshotCriteria = Optional.of(value))

    override def withRetentionCriteria(
      value: RetentionCriteria): PersistenceEffectorConfig[S, E, M] =
      copy(retentionCriteria = Optional.of(value))

    override def withBackoffConfig(value: BackoffConfig): PersistenceEffectorConfig[S, E, M] =
      copy(backoffConfig = Optional.of(value))

    override def withMessageConverter(
      value: MessageConverter[S, E, M]): PersistenceEffectorConfig[S, E, M] =
      copy(messageConverter = value)
  }

  /**
   * Create a PersistenceEffectorConfig with minimal required parameters.
   * Uses default values for optional parameters.
   *
   * @param persistenceId Persistence ID for the effector
   * @param initialState Initial state
   * @param applyEvent Function to apply events to state
   * @tparam S Type of state
   * @tparam E Type of event
   * @tparam M Type of message
   * @return PersistenceEffectorConfig instance
   */
  def create[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: java.util.function.BiFunction[S, E, S]): PersistenceEffectorConfig[S, E, M] =
    create(
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      persistenceMode = PersistenceMode.PERSISTENCE,
      stashSize = Int.MaxValue,
      snapshotCriteria = Optional.empty(),
      retentionCriteria = Optional.empty(),
      backoffConfig = Optional.empty(),
      messageConverter = MessageConverter.defaultFunctions[S, E, M],
    )

  /**
   * Create a PersistenceEffectorConfig with all parameters specified.
   *
   * @param persistenceId Persistence ID for the effector
   * @param initialState Initial state
   * @param applyEvent Function to apply events to state
   * @param persistenceMode Persistence mode
   * @param stashSize Stash size
   * @param snapshotCriteria Snapshot criteria
   * @param retentionCriteria Retention criteria
   * @param backoffConfig Backoff configuration
   * @param messageConverter Message converter
   * @tparam S Type of state
   * @tparam E Type of event
   * @tparam M Type of message
   * @return PersistenceEffectorConfig instance
   */
  def create[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: java.util.function.BiFunction[S, E, S],
    persistenceMode: PersistenceMode,
    stashSize: Int,
    snapshotCriteria: Optional[SnapshotCriteria[S, E]],
    retentionCriteria: Optional[RetentionCriteria],
    backoffConfig: Optional[BackoffConfig],
    messageConverter: MessageConverter[S, E, M],
  ): PersistenceEffectorConfig[S, E, M] =
    new Impl[S, E, M](
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      persistenceMode = persistenceMode,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
      backoffConfig = backoffConfig,
      messageConverter = messageConverter,
    )

}

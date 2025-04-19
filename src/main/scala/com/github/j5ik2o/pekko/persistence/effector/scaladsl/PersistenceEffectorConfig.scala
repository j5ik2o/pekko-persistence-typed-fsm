package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Configuration for PersistenceEffector in Scala API.
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
  def applyEvent: (S, E) => S

  /**
   * Get the persistence mode.
   * Determines whether events are persisted to disk or kept in memory.
   *
   * @return Persistence mode (Persisted or Ephemeral)
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
  def snapshotCriteria: Option[SnapshotCriteria[S, E]]
  
  /**
   * Get the retention criteria.
   * Determines how many snapshots and events should be kept.
   *
   * @return Optional retention criteria
   */
  def retentionCriteria: Option[RetentionCriteria]
  
  /**
   * Get the backoff configuration.
   * Determines how to handle failures with backoff.
   *
   * @return Optional backoff configuration
   */
  def backoffConfig: Option[BackoffConfig]
  
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
  def wrapPersistedEvents: Seq[E] => M
  
  /**
   * Get the function to wrap a persisted snapshot into a message.
   *
   * @return Function to wrap snapshot
   */
  def wrapPersistedSnapshot: S => M
  
  /**
   * Get the function to wrap a recovered state into a message.
   *
   * @return Function to wrap recovered state
   */
  def wrapRecoveredState: S => M
  
  /**
   * Get the function to wrap deleted snapshots information into a message.
   *
   * @return Function to wrap deleted snapshots information
   */
  def wrapDeleteSnapshots: Long => M
  
  /**
   * Get the function to extract persisted events from a message.
   *
   * @return Function to extract events
   */
  def unwrapPersistedEvents: M => Option[Seq[E]]
  
  /**
   * Get the function to extract persisted snapshot from a message.
   *
   * @return Function to extract snapshot
   */
  def unwrapPersistedSnapshot: M => Option[S]
  
  /**
   * Get the function to extract recovered state from a message.
   *
   * @return Function to extract recovered state
   */
  def unwrapRecoveredState: M => Option[S]
  
  /**
   * Get the function to extract deleted snapshots information from a message.
   *
   * @return Function to extract deleted snapshots information
   */
  def unwrapDeleteSnapshots: M => Option[Long]

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
}

/**
 * Companion object for PersistenceEffectorConfig.
 * Provides factory methods to create configurations.
 */
object PersistenceEffectorConfig {
  private final case class Impl[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    persistenceMode: PersistenceMode,
    stashSize: Int,
    snapshotCriteria: Option[SnapshotCriteria[S, E]],
    retentionCriteria: Option[RetentionCriteria],
    backoffConfig: Option[BackoffConfig],
    messageConverter: MessageConverter[S, E, M],
  ) extends PersistenceEffectorConfig[S, E, M] {
    override def wrapPersistedEvents: Seq[E] => M = messageConverter.wrapPersistedEvents
    override def wrapPersistedSnapshot: S => M = messageConverter.wrapPersistedSnapshot
    override def wrapRecoveredState: S => M = messageConverter.wrapRecoveredState
    override def wrapDeleteSnapshots: Long => M = messageConverter.wrapDeleteSnapshots
    override def unwrapPersistedEvents: M => Option[Seq[E]] = messageConverter.unwrapPersistedEvents
    override def unwrapPersistedSnapshot: M => Option[S] = messageConverter.unwrapPersistedSnapshot
    override def unwrapRecoveredState: M => Option[S] = messageConverter.unwrapRecoveredState
    override def unwrapDeleteSnapshots: M => Option[Long] = messageConverter.unwrapDeleteSnapshots

    override def withPersistenceMode(value: PersistenceMode): PersistenceEffectorConfig[S, E, M] =
      copy(persistenceMode = value)

    override def withStashSize(value: Int): PersistenceEffectorConfig[S, E, M] =
      copy(stashSize = value)

    override def withSnapshotCriteria(
      value: SnapshotCriteria[S, E]): PersistenceEffectorConfig[S, E, M] =
      copy(snapshotCriteria = Some(value))

    override def withRetentionCriteria(
      value: RetentionCriteria): PersistenceEffectorConfig[S, E, M] =
      copy(retentionCriteria = Some(value))

    override def withBackoffConfig(value: BackoffConfig): PersistenceEffectorConfig[S, E, M] =
      copy(backoffConfig = Some(value))

    override def withMessageConverter(
      value: MessageConverter[S, E, M]): PersistenceEffectorConfig[S, E, M] =
      copy(messageConverter = value)
  }

  /**
   * Create a PersistenceEffectorConfig with specified parameters.
   * Provides default values for optional parameters.
   *
   * @param persistenceId Persistence ID for the effector
   * @param initialState Initial state
   * @param applyEvent Function to apply events to state
   * @param persistenceMode Persistence mode (default: Persisted)
   * @param stashSize Stash size (default: Int.MaxValue)
   * @param snapshotCriteria Snapshot criteria (default: None)
   * @param retentionCriteria Retention criteria (default: None)
   * @param backoffConfig Backoff configuration (default: None)
   * @param messageConverter Message converter (default: default functions)
   * @tparam S Type of state
   * @tparam E Type of event
   * @tparam M Type of message
   * @return PersistenceEffectorConfig instance
   */
  def create[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    persistenceMode: PersistenceMode = PersistenceMode.Persisted,
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
    retentionCriteria: Option[RetentionCriteria] = None,
    backoffConfig: Option[BackoffConfig] = None,
    messageConverter: MessageConverter[S, E, M] = MessageConverter.defaultFunctions[S, E, M],
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

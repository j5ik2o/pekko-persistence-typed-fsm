package com.github.j5ik2o.pekko.persistence.effector.scaladsl

trait PersistenceEffectorConfig[S, E, M] {
  def persistenceId: String

  def initialState: S

  def applyEvent: (S, E) => S

  def persistenceMode: PersistenceMode
  def stashSize: Int
  def snapshotCriteria: Option[SnapshotCriteria[S, E]]
  def retentionCriteria: Option[RetentionCriteria]
  def backoffConfig: Option[BackoffConfig]
  def messageConverter: MessageConverter[S, E, M]

  // メッセージコンバーター関連のメソッド
  def wrapPersistedEvents: Seq[E] => M
  def wrapPersistedSnapshot: S => M
  def wrapRecoveredState: S => M
  def wrapDeleteSnapshots: Long => M
  def unwrapPersistedEvents: M => Option[Seq[E]]
  def unwrapPersistedSnapshot: M => Option[S]
  def unwrapRecoveredState: M => Option[S]
  def unwrapDeleteSnapshots: M => Option[Long]

  def withPersistenceMode(value: PersistenceMode): PersistenceEffectorConfig[S, E, M]
  def withStashSize(value: Int): PersistenceEffectorConfig[S, E, M]
  def withSnapshotCriteria(value: SnapshotCriteria[S, E]): PersistenceEffectorConfig[S, E, M]
  def withRetentionCriteria(value: RetentionCriteria): PersistenceEffectorConfig[S, E, M]
  def withBackoffConfig(value: BackoffConfig): PersistenceEffectorConfig[S, E, M]
  def withMessageConverter(value: MessageConverter[S, E, M]): PersistenceEffectorConfig[S, E, M]
}
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

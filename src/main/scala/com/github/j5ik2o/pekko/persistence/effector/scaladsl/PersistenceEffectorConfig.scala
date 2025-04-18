package com.github.j5ik2o.pekko.persistence.effector.scaladsl

final case class PersistenceEffectorConfig[S, E, M] private (
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Option[SnapshotCriteria[S, E]],
  retentionCriteria: Option[RetentionCriteria],
  backoffConfig: Option[BackoffConfig],
  messageConverter: MessageConverter[S, E, M],
) {
  def wrapPersistedEvents: Seq[E] => M = messageConverter.wrapPersistedEvents
  def wrapPersistedSnapshot: S => M = messageConverter.wrapPersistedSnapshot
  def wrapRecoveredState: S => M = messageConverter.wrapRecoveredState
  def wrapDeleteSnapshots: Long => M = messageConverter.wrapDeleteSnapshots
  def unwrapPersistedEvents: M => Option[Seq[E]] = messageConverter.unwrapPersistedEvents
  def unwrapPersistedSnapshot: M => Option[S] = messageConverter.unwrapPersistedSnapshot
  def unwrapRecoveredState: M => Option[S] = messageConverter.unwrapRecoveredState
  def unwrapDeleteSnapshots: M => Option[Long] = messageConverter.unwrapDeleteSnapshots
}

object PersistenceEffectorConfig {
  def create[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    persistenceMode: PersistenceMode,
    stashSize: Int,
    snapshotCriteria: Option[SnapshotCriteria[S, E]],
    retentionCriteria: Option[RetentionCriteria],
    backoffConfig: Option[BackoffConfig],
    messageConverter: MessageConverter[S, E, M],
  ): PersistenceEffectorConfig[S, E, M] =
    new PersistenceEffectorConfig[S, E, M](
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

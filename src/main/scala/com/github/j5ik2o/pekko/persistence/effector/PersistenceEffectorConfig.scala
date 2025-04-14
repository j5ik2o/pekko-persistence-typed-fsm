package com.github.j5ik2o.pekko.persistence.effector

final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  messageConverter: MessageConverter[S, E, M],
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
  retentionCriteria: Option[RetentionCriteria] = None,
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

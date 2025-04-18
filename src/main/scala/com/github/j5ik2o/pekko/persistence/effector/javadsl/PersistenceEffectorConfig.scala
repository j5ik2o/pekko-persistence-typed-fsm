package com.github.j5ik2o.pekko.persistence.effector.javadsl

import java.util.Optional

final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: java.util.function.BiFunction[S, E, S],
  messageConverter: MessageConverter[S, E, M],
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Optional[SnapshotCriteria[S, E]] = Optional.empty(),
  retentionCriteria: Optional[RetentionCriteria] = Optional.empty(),
  backoffConfig: Optional[BackoffConfig] = Optional.empty(),
) {
  def wrapPersistedEvents: java.util.List[E] => M = messageConverter.wrapPersistedEvents
  def wrapPersistedSnapshot: java.util.function.Function[S, M] =
    messageConverter.wrapPersistedSnapshot
  def wrapRecoveredState: java.util.function.Function[S, M] = messageConverter.wrapRecoveredState
  def wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M] =
    messageConverter.wrapDeleteSnapshots
  def unwrapPersistedEvents: java.util.function.Function[M, Option[java.util.List[E]]] =
    messageConverter.unwrapPersistedEvents
  def unwrapPersistedSnapshot: java.util.function.Function[M, Option[S]] =
    messageConverter.unwrapPersistedSnapshot
  def unwrapRecoveredState: java.util.function.Function[M, Option[S]] =
    messageConverter.unwrapRecoveredState
  def unwrapDeleteSnapshots: java.util.function.Function[M, Option[java.lang.Long]] =
    messageConverter.unwrapDeleteSnapshots
}

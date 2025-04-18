package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffectorConfig as SPersistenceEffectorConfig,
  PersistenceMode as SPersistenceMode,
}

import java.util.Optional
import scala.jdk.OptionConverters.*

final case class PersistenceEffectorConfig[S, E, M] private (
  persistenceId: String,
  initialState: S,
  applyEvent: java.util.function.BiFunction[S, E, S],
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Optional[SnapshotCriteria[S, E]] = Optional.empty(),
  retentionCriteria: Optional[RetentionCriteria] = Optional.empty(),
  backoffConfig: Optional[BackoffConfig] = Optional.empty(),
  messageConverter: MessageConverter[S, E, M],
) {
  def toScala: SPersistenceEffectorConfig[S, E, M] = {
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
  def wrapPersistedEvents: java.util.function.Function[java.util.List[E], M] =
    messageConverter.wrapPersistedEvents
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

object PersistenceEffectorConfig {
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

  def create[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: java.util.function.BiFunction[S, E, S],
    persistenceMode: PersistenceMode,
    stashSize: Int,
    snapshotCriteria: Optional[SnapshotCriteria[S, E]],
    retentionCriteria: Optional[RetentionCriteria],
    backoffConfig: Optional[BackoffConfig],
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
      messageConverter = MessageConverter.defaultFunction,
    )
}

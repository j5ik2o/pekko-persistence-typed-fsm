package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffectorConfig as SPersistenceEffectorConfig,
  PersistenceMode as SPersistenceMode,
}

import java.util.Optional
import scala.jdk.OptionConverters.*

trait PersistenceEffectorConfig[S, E, M] {
  def persistenceId: String
  def initialState: S
  def applyEvent: java.util.function.BiFunction[S, E, S]
  def persistenceMode: PersistenceMode
  def stashSize: Int
  def snapshotCriteria: Optional[SnapshotCriteria[S, E]]
  def retentionCriteria: Optional[RetentionCriteria]
  def backoffConfig: Optional[BackoffConfig]
  def messageConverter: MessageConverter[S, E, M]

  def wrapPersistedEvents: java.util.function.Function[java.util.List[E], M]
  def wrapPersistedSnapshot: java.util.function.Function[S, M]
  def wrapRecoveredState: java.util.function.Function[S, M]
  def wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M]

  def unwrapPersistedEvents: java.util.function.Function[M, Option[java.util.List[E]]]
  def unwrapPersistedSnapshot: java.util.function.Function[M, Option[S]]
  def unwrapRecoveredState: java.util.function.Function[M, Option[S]]
  def unwrapDeleteSnapshots: java.util.function.Function[M, Option[java.lang.Long]]

  def withPersistenceMode(value: PersistenceMode): PersistenceEffectorConfig[S, E, M]
  def withStashSize(value: Int): PersistenceEffectorConfig[S, E, M]
  def withSnapshotCriteria(value: SnapshotCriteria[S, E]): PersistenceEffectorConfig[S, E, M]
  def withRetentionCriteria(value: RetentionCriteria): PersistenceEffectorConfig[S, E, M]
  def withBackoffConfig(value: BackoffConfig): PersistenceEffectorConfig[S, E, M]
  def withMessageConverter(value: MessageConverter[S, E, M]): PersistenceEffectorConfig[S, E, M]

  def toScala: SPersistenceEffectorConfig[S, E, M]
}

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

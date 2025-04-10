package com.github.j5ik2o.pekko.persistence.effector

final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  wrapPersistedEvents: Seq[E] => M,
  wrapPersistedSnapshot: S => M,
  wrapRecoveredState: S => M,
  unwrapPersistedEvents: M => Option[Seq[E]],
  unwrapPersistedSnapshot: M => Option[S],
  unwrapRecoveredState: M => Option[S],
  persistenceMode: PersistenceMode,
  stashSize: Int,
)

object PersistenceEffectorConfig {
  def apply[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    wrapPersistedEvents: Seq[E] => M,
    wrapPersistedSnapshot: S => M,
    wrapRecoveredState: S => M,
    unwrapPersistedEvents: M => Option[Seq[E]],
    unwrapPersistedSnapshot: M => Option[S],
    unwrapRecoveredState: M => Option[S],
    persistenceMode: PersistenceMode,
    stashSize: Int = 32): PersistenceEffectorConfig[S, E, M] =
    new PersistenceEffectorConfig(
      persistenceId,
      initialState,
      applyEvent,
      wrapPersistedEvents,
      wrapPersistedSnapshot,
      wrapRecoveredState,
      unwrapPersistedEvents,
      unwrapPersistedSnapshot,
      unwrapRecoveredState,
      persistenceMode,
      stashSize,
    )

  def applyWithMessageConverter[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
    persistenceMode: PersistenceMode,
    stashSize: Int = 32): PersistenceEffectorConfig[S, E, M] = apply(
    persistenceId,
    initialState,
    applyEvent,
    messageConverter.wrapPersistedEvents,
    messageConverter.wrapPersistedState,
    messageConverter.wrapRecoveredState,
    messageConverter.unwrapPersistedEvents,
    messageConverter.unwrapPersistedState,
    messageConverter.unwrapRecoveredState,
    persistenceMode,
    stashSize,
  )
}

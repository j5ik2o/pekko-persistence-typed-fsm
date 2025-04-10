package com.github.j5ik2o.pekko.persistence.typed.fsm

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
  stashSize: Int = 32,
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
    unwrapRecoveredState: M => Option[S]): PersistenceEffectorConfig[S, E, M] =
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
    )

  def apply[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M]): PersistenceEffectorConfig[S, E, M] = apply(
    persistenceId,
    initialState,
    applyEvent,
    messageConverter.wrapPersistedEvents,
    messageConverter.wrapPersistedState,
    messageConverter.wrapRecoveredState,
    messageConverter.unwrapPersistedEvents,
    messageConverter.unwrapPersistedState,
    messageConverter.unwrapRecoveredState,
  )
}

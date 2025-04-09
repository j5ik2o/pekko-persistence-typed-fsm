package com.github.j5ik2o.pekko.persistence.typed.fsm

final case class EffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  wrapPersisted: Seq[E] => M,
  wrapRecovered: S => M,
  unwrapPersisted: M => Option[Seq[E]],
  unwrapRecovered: M => Option[S],
  stashSize: Int = 32,
)

object EffectorConfig {
  def apply[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    wrapPersisted: Seq[E] => M,
    wrapRecovered: S => M,
    unwrapPersisted: M => Option[Seq[E]],
    unwrapRecovered: M => Option[S]): EffectorConfig[S, E, M] = new EffectorConfig(
    persistenceId,
    initialState,
    applyEvent,
    wrapPersisted,
    wrapRecovered,
    unwrapPersisted,
    unwrapRecovered,
  )

  def apply[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M]): EffectorConfig[S, E, M] = apply(
    persistenceId,
    initialState,
    applyEvent,
    messageConverter.wrapPersisted,
    messageConverter.wrapRecovered,
    messageConverter.unwrapPersisted,
    messageConverter.unwrapRecovered,
  )
}

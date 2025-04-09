package com.github.j5ik2o.eff.sm.splitter

final case class EffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  wrapPersisted: Seq[E] => M,
  wrapRecovered: S => M,
  unwrapPersisted: M => Option[Seq[E]],
  unwrapRecovered: M => Option[S],
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
    wrappedISO: WrappedISO[S, E, M]): EffectorConfig[S, E, M] = apply(
    persistenceId,
    initialState,
    applyEvent,
    wrappedISO.wrapPersisted,
    wrappedISO.wrapRecovered,
    wrappedISO.unwrapPersisted,
    wrappedISO.unwrapRecovered,
  )
}

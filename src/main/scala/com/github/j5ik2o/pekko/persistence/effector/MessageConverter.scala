package com.github.j5ik2o.pekko.persistence.effector

trait MessageConverter[S, E, M <: Matchable] {

  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedState(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]

  def unwrapPersistedEvents(message: M): Option[Seq[E]] = message match {
    case msg: PersistedEvent[E, M] @unchecked => Some(msg.events)
    case _ => None
  }

  def unwrapPersistedState(message: M): Option[S] = message match {
    case msg: PersistedState[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

  def unwrapRecoveredState(message: M): Option[S] = message match {
    case msg: RecoveredState[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

}

object MessageConverter {
  private final case class Default[S, E, M <: Matchable](
    _wrapPersistedEvents: Seq[E] => M & PersistedEvent[E, M],
    _wrapPersistedState: S => M & PersistedState[S, M],
    _wrapRecoveredState: S => M & RecoveredState[S, M],
  ) extends MessageConverter[S, E, M] {
    override def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M] =
      _wrapPersistedEvents(events)
    override def wrapPersistedState(state: S): M & PersistedState[S, M] = _wrapPersistedState(state)
    override def wrapRecoveredState(state: S): M & RecoveredState[S, M] = _wrapRecoveredState(state)
  }

  def apply[S, E, M <: Matchable](
    wrapPersistedEvents: Seq[E] => M & PersistedEvent[E, M],
    wrapPersistedState: S => M & PersistedState[S, M],
    wrapRecoveredState: S => M & RecoveredState[S, M],
  ): MessageConverter[S, E, M] =
    Default(wrapPersistedEvents, wrapPersistedState, wrapRecoveredState)
}

package com.github.j5ik2o.pekko.persistence.typed.fsm

// 相互変換可能なISOを定義するトレイト - 余分な型パラメータを削除
trait MessageConverter[S, E, M <: Matchable] {

  def wrapPersisted(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapRecovered(state: S): M & RecoveredState[S, M]

  def unwrapPersisted(message: M): Option[Seq[E]] = message match {
    case msg: PersistedEvent[E, M] @unchecked => Some(msg.events)
    case _ => None
  }

  def unwrapRecovered(message: M): Option[S] = message match {
    case msg: RecoveredState[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

}

object MessageConverter {
  private final case class Default[S, E, M <: Matchable](
    _wrapPersisted: Seq[E] => M & PersistedEvent[E, M],
    _wrapRecovered: S => M & RecoveredState[S, M],
  ) extends MessageConverter[S, E, M] {
    override def wrapPersisted(events: Seq[E]): M & PersistedEvent[E, M] =
      _wrapPersisted(events)
    override def wrapRecovered(state: S): M & RecoveredState[S, M] = _wrapRecovered(state)
  }

  def apply[S, E, M <: Matchable](
    wrapPersisted: Seq[E] => M & PersistedEvent[E, M],
    wrapRecovered: S => M & RecoveredState[S, M],
  ): MessageConverter[S, E, M] = Default(wrapPersisted, wrapRecovered)
}

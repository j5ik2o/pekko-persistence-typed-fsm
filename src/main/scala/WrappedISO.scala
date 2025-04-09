package com.github.j5ik2o.eff.sm.splitter

// 相互変換可能なISOを定義するトレイト - 余分な型パラメータを削除
trait WrappedISO[S, E, M <: Matchable] {

  def wrapPersisted(events: Seq[E]): M & WrappedPersisted[E, M]
  def wrapRecovered(state: S): M & WrappedRecovered[S, M]

  def unwrapPersisted(message: M): Option[Seq[E]] = message match {
    case msg: WrappedPersisted[E, M] @unchecked => Some(msg.events)
    case _ => None
  }

  def unwrapRecovered(message: M): Option[S] = message match {
    case msg: WrappedRecovered[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

}

object WrappedISO {
  private final case class Default[S, E, M <: Matchable](
    _wrapPersisted: Seq[E] => M & WrappedPersisted[E, M],
    _wrapRecovered: S => M & WrappedRecovered[S, M],
  ) extends WrappedISO[S, E, M] {
    override def wrapPersisted(events: Seq[E]): M & WrappedPersisted[E, M] =
      _wrapPersisted(events)
    override def wrapRecovered(state: S): M & WrappedRecovered[S, M] = _wrapRecovered(state)
  }

  def apply[S, E, M <: Matchable](
    wrapPersisted: Seq[E] => M & WrappedPersisted[E, M],
    wrapRecovered: S => M & WrappedRecovered[S, M],
  ): WrappedISO[S, E, M] = Default(wrapPersisted, wrapRecovered)
}

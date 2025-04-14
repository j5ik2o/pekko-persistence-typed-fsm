package com.github.j5ik2o.pekko.persistence.effector

import com.github.j5ik2o.pekko.persistence.effector.PersistenceStoreActor.PersistenceCommand

import scala.compiletime.asMatchable

trait MessageConverter[S, E, M] {
  // def wrapMessage(p: PersistenceCommand[S, E]): M & MessageWrapper[M]

  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedSnapshot(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]
  def wrapDeleteSnapshots(maxSequenceNumber: Long): M & DeletedSnapshots[M]

  def unwrapPersistedEvents(message: M): Option[Seq[E]] = message.asMatchable match {
    case msg: PersistedEvent[E, M] @unchecked => Some(msg.events)
    case _ => None
  }

  def unwrapPersistedSnapshot(message: M): Option[S] = message.asMatchable match {
    case msg: PersistedState[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

  def unwrapRecoveredState(message: M): Option[S] = message.asMatchable match {
    case msg: RecoveredState[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

  def unwrapDeleteSnapshots(message: M): Option[Long] = message.asMatchable match {
    case msg: DeletedSnapshots[M] @unchecked => Some(msg.maxSequenceNumber)
    case _ => None
  }

}

object MessageConverter {
  private final case class Default[S, E, M <: Matchable](
    _wrapPersistedEvents: Seq[E] => M & PersistedEvent[E, M],
    _wrapPersistedState: S => M & PersistedState[S, M],
    _wrapRecoveredState: S => M & RecoveredState[S, M],
    _wrapDeleteSnapshots: Long => M & DeletedSnapshots[M],
  ) extends MessageConverter[S, E, M] {
    override def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M] =
      _wrapPersistedEvents(events)
    override def wrapPersistedSnapshot(state: S): M & PersistedState[S, M] = _wrapPersistedState(
      state)
    override def wrapRecoveredState(state: S): M & RecoveredState[S, M] = _wrapRecoveredState(state)
    override def wrapDeleteSnapshots(maxSequenceNumber: Long): M & DeletedSnapshots[M] =
      _wrapDeleteSnapshots(maxSequenceNumber)
  }

  def apply[S, E, M <: Matchable](
    wrapPersistedEvents: Seq[E] => M & PersistedEvent[E, M],
    wrapPersistedState: S => M & PersistedState[S, M],
    wrapRecoveredState: S => M & RecoveredState[S, M],
    wrapDeleteSnapshots: Long => M & DeletedSnapshots[M],
  ): MessageConverter[S, E, M] =
    Default(wrapPersistedEvents, wrapPersistedState, wrapRecoveredState, wrapDeleteSnapshots)
}

package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  MessageConverter as SMessageConverter,
  PersistedEvent as SPersistedEvent,
  PersistedState as SPersistedState,
  RecoveredState as SRecoveredState,
  DeletedSnapshots as SDeletedSnapshots
}
import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

trait MessageConverter[S, E, M] {
  def wrapPersistedEvents(events: java.util.List[E]): M & PersistedEvent[E, M]

  def wrapPersistedSnapshot(state: S): M & PersistedState[S, M]

  def wrapRecoveredState(state: S): M & RecoveredState[S, M]

  def wrapDeleteSnapshots(maxSequenceNumber: java.lang.Long): M & DeletedSnapshots[M]

  def unwrapPersistedEvents(message: M): Option[java.util.List[E]] = message.asMatchable match {
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

  def unwrapDeleteSnapshots(message: M): Option[java.lang.Long] = message.asMatchable match {
    case msg: DeletedSnapshots[M] @unchecked => Some(msg.maxSequenceNumber)
    case _ => None
  }

  def toScala: SMessageConverter[S, E, M] = {
    val self = this
    new SMessageConverter[S, E, M] {
      override def wrapPersistedEvents(events: Seq[E]): M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M] = {
        val javaEvents = events.asJava
        val result = self.wrapPersistedEvents(javaEvents)
        result.toScala.asInstanceOf[M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M]]
      }
      override def wrapPersistedSnapshot(state: S): M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] = {
        val result = self.wrapPersistedSnapshot(state)
        result.toScala.asInstanceOf[M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M]]
      }
      override def wrapRecoveredState(state: S): M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] = {
        val result = self.wrapRecoveredState(state)
        result.toScala.asInstanceOf[M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M]]
      }
      override def wrapDeleteSnapshots(maxSequenceNumber: Long): M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] = {
        val javaLong = maxSequenceNumber.asInstanceOf[java.lang.Long]
        val result = self.wrapDeleteSnapshots(javaLong)
        result.toScala.asInstanceOf[M & com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M]]
      }
    }
  }

}

object MessageConverter {
  private final case class Default[S, E, M <: Matchable](
    _wrapPersistedEvents: java.util.function.Function[java.util.List[E], M & PersistedEvent[E, M]],
    _wrapPersistedState: java.util.function.Function[S, M & PersistedState[S, M]],
    _wrapRecoveredState: java.util.function.Function[S, M & RecoveredState[S, M]],
    _wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M & DeletedSnapshots[M]],
  ) extends MessageConverter[S, E, M] {
    override def wrapPersistedEvents(events: java.util.List[E]): M & PersistedEvent[E, M] =
      _wrapPersistedEvents(events)

    override def wrapPersistedSnapshot(state: S): M & PersistedState[S, M] = _wrapPersistedState(
      state)

    override def wrapRecoveredState(state: S): M & RecoveredState[S, M] = _wrapRecoveredState(state)

    override def wrapDeleteSnapshots(maxSequenceNumber: java.lang.Long): M & DeletedSnapshots[M] =
      _wrapDeleteSnapshots(maxSequenceNumber)
  }

  def create[S, E, M <: Matchable](
    wrapPersistedEvents: java.util.function.Function[java.util.List[E], M & PersistedEvent[E, M]],
    wrapPersistedState: java.util.function.Function[S, M & PersistedState[S, M]],
    wrapRecoveredState: java.util.function.Function[S, M & RecoveredState[S, M]],
    wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M & DeletedSnapshots[M]],
  ): MessageConverter[S, E, M] =
    Default(wrapPersistedEvents, wrapPersistedState, wrapRecoveredState, wrapDeleteSnapshots)
}

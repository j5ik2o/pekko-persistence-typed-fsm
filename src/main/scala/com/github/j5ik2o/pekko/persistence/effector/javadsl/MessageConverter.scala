package com.github.j5ik2o.pekko.persistence.effector.javadsl

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

  def toScala: com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageConverter[S, E, M] = {
    val self = this
    new com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageConverter[S, E, M] {
      override def wrapPersistedEvents(events: Seq[E]): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M] = {
        val javaEvents = events.asJava
        val result = self.wrapPersistedEvents(javaEvents)
        // JavaDSL版のPersistedEventをScalaDSL版に変換するアダプターを作成
        val adapter = new MessageWrapperAdapter.JavaPersistedEventAdapter[E, M](result)
        // 元のメッセージとアダプターを合成して返す
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M]]
      }

      override def unwrapPersistedEvents(message: M): Option[Seq[E]] =
        self.unwrapPersistedEvents(message).map(_.asScala.toSeq)

      override def wrapPersistedSnapshot(state: S): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] = {
        val result = self.wrapPersistedSnapshot(state)
        // JavaDSL版のPersistedStateをScalaDSL版に変換するアダプターを作成
        val adapter = new MessageWrapperAdapter.JavaPersistedStateAdapter[S, M](result)
        // 元のメッセージとアダプターを合成して返す
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M]]
      }

      override def unwrapPersistedSnapshot(message: M): Option[S] =
        self.unwrapPersistedSnapshot(message)

      override def wrapRecoveredState(state: S): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] = {
        val result = self.wrapRecoveredState(state)
        // JavaDSL版のRecoveredStateをScalaDSL版に変換するアダプターを作成
        val adapter = new MessageWrapperAdapter.JavaRecoveredStateAdapter[S, M](result)
        // 元のメッセージとアダプターを合成して返す
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M]]
      }

      override def unwrapRecoveredState(message: M): Option[S] =
        self.unwrapRecoveredState(message)

      override def wrapDeleteSnapshots(maxSequenceNumber: Long): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] = {
        val javaLong = maxSequenceNumber.asInstanceOf[java.lang.Long]
        val result = self.wrapDeleteSnapshots(javaLong)
        // JavaDSL版のDeletedSnapshotsをScalaDSL版に変換するアダプターを作成
        val adapter = new MessageWrapperAdapter.JavaDeletedSnapshotsAdapter[M](result)
        // 元のメッセージとアダプターを合成して返す
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M]]
      }

      override def unwrapDeleteSnapshots(message: M): Option[Long] =
        self.unwrapDeleteSnapshots(message).map(_.asInstanceOf[Long])
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

  // 標準的なメッセージラッパークラス（内部パッケージに移動することも検討）
  private[effector] class StandardJavaPersistedEvent[E](val events: java.util.List[E])
    extends PersistedEvent[E, Any]

  private[effector] class StandardJavaPersistedState[S](val state: S) extends PersistedState[S, Any]

  private[effector] class StandardJavaRecoveredState[S](val state: S) extends RecoveredState[S, Any]

  private[effector] class StandardJavaDeletedSnapshots(val maxSequenceNumber: Long)
    extends DeletedSnapshots[Any]

  def defaultFunction[S, E, M]: MessageConverter[S, E, M] =
    new MessageConverter[S, E, M] {
      override def wrapPersistedEvents(events: java.util.List[E]): M & PersistedEvent[E, M] =
        new StandardJavaPersistedEvent[E](events).asInstanceOf[M & PersistedEvent[E, M]]

      override def wrapPersistedSnapshot(state: S): M & PersistedState[S, M] =
        new StandardJavaPersistedState[S](state).asInstanceOf[M & PersistedState[S, M]]

      override def wrapRecoveredState(state: S): M & RecoveredState[S, M] =
        new StandardJavaRecoveredState[S](state).asInstanceOf[M & RecoveredState[S, M]]

      override def wrapDeleteSnapshots(maxSequenceNumber: java.lang.Long): M & DeletedSnapshots[M] =
        new StandardJavaDeletedSnapshots(maxSequenceNumber).asInstanceOf[M & DeletedSnapshots[M]]
    }
}

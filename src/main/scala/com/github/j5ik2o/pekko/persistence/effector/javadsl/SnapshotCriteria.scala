package com.github.j5ik2o.pekko.persistence.effector.javadsl

import scala.jdk.CollectionConverters.*
import com.github.j5ik2o.pekko.persistence.effector.scaladsl

sealed trait SnapshotCriteria[S, E] {
  def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean
  def toScala: scaladsl.SnapshotCriteria[S, E]
}

object SnapshotCriteria {

  @FunctionalInterface
  trait TriFunction[T1, T2, T3, R] {
    def apply(t1: T1, t2: T2, t3: T3): R
  }

  private[effector] final case class JEventBased[S, E](
    predicate: TriFunction[E, S, Long, Boolean]
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      predicate.apply(event, state, sequenceNumber)

    override def toScala: scaladsl.SnapshotCriteria[S, E] =
      scaladsl.SnapshotCriteria.EventBased((e: E, s: S, l: Long) => predicate.apply(e, s, l))
  }

  private[effector] final case class JCountBased[S, E](
    every: Int
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      sequenceNumber % every == 0

    override def toScala: scaladsl.SnapshotCriteria[S, E] =
      scaladsl.SnapshotCriteria.CountBased(every)
  }

  private[effector] final case class JCombined[S, E](
    criteria: java.util.List[SnapshotCriteria[S, E]],
    requireAll: Boolean
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean = {
      val results = criteria.asScala.map(_.shouldTakeSnapshot(event, state, sequenceNumber))
      if (requireAll) results.forall(identity) else results.exists(identity)
    }

    override def toScala: scaladsl.SnapshotCriteria[S, E] =
      scaladsl.SnapshotCriteria.Combined(
        criteria.asScala.map(_.toScala).toSeq,
        requireAll
      )
  }

  def eventBased[S, E](predicate: TriFunction[E, S, Long, Boolean]): SnapshotCriteria[S, E] =
    JEventBased(predicate)

  def countBased[S, E](every: Int): SnapshotCriteria[S, E] = {
    require(every > 0, "every must be greater than 0")
    JCountBased(every)
  }

  def combined[S, E](criteria: java.util.List[SnapshotCriteria[S, E]], requireAll: Boolean): SnapshotCriteria[S, E] =
    JCombined(criteria, requireAll)

  def always[S, E](): SnapshotCriteria[S, E] =
    eventBased((unused1: E, unused2: S, unused3: Long) => true)

  def onEventType[S, E](eventClass: Class[?]): SnapshotCriteria[S, E] =
    eventBased((evt: E, unused: S, unused2: Long) => eventClass.isInstance(evt))

  def every[S, E](nth: Int): SnapshotCriteria[S, E] =
    countBased(nth)

  def fromScala[S, E](criteria: scaladsl.SnapshotCriteria[S, E]): SnapshotCriteria[S, E] = criteria match {
    case scaladsl.SnapshotCriteria.EventBased(pred) =>
      eventBased((e: E, s: S, l: Long) => pred(e, s, l))
    case scaladsl.SnapshotCriteria.CountBased(n) =>
      countBased(n)
    case scaladsl.SnapshotCriteria.Combined(c, r) =>
      combined(c.map(fromScala[S, E]).asJava, r)
  }
}

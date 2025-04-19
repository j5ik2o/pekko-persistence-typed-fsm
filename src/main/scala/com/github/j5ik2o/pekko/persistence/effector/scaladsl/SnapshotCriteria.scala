package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Trait defining snapshot strategy. Represents conditions for determining whether a snapshot should be taken
 */
sealed trait SnapshotCriteria[S, E] {

  /**
   * Determine whether a snapshot should be taken
   *
   * @param event
   *   Event
   * @param state
   *   Current state
   * @param sequenceNumber
   *   Sequence number
   * @return
   *   true if a snapshot should be taken
   */
  def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean
}

/**
 * Companion object providing factory methods and default implementations for snapshot strategy
 */
object SnapshotCriteria {

  /**
   * Strategy to determine whether to take a snapshot based on event content
   *
   * @param predicate
   *   Predicate function to determine whether a snapshot should be taken
   */
  final case class EventBased[S, E](
    predicate: (E, S, Long) => Boolean,
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      predicate(event, state, sequenceNumber)
  }

  /**
   * Strategy to determine whether to take a snapshot based on event count
   *
   * @param every
   *   Take a snapshot every N events
   */
  final case class CountBased[S, E](
    every: Int,
  ) extends SnapshotCriteria[S, E] {
    require(every > 0, "every must be greater than 0")

    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      sequenceNumber % every == 0
  }

  /**
   * Strategy combining multiple conditions
   *
   * @param criteria
   *   List of conditions
   * @param requireAll
   *   If true, all conditions must be met (AND condition), if false, any condition can be met (OR condition)
   */
  final case class Combined[S, E](
    criteria: Seq[SnapshotCriteria[S, E]],
    requireAll: Boolean = false,
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean = {
      val results = criteria.map(_.shouldTakeSnapshot(event, state, sequenceNumber))
      if (requireAll) results.forall(identity) else results.exists(identity)
    }
  }

  /**
   * Strategy to always take a snapshot
   */
  def always[S, E]: SnapshotCriteria[S, E] =
    EventBased[S, E]((unused1, unused2, unused3) => true)

  /**
   * Strategy to take a snapshot only for specific event types
   *
   * @param eventClass
   *   Class of events for which to take a snapshot
   */
  def onEventType[S, E](eventClass: Class[?]): SnapshotCriteria[S, E] =
    EventBased[S, E]((evt, unused, unused2) => eventClass.isInstance(evt))

  /**
   * Strategy to take a snapshot every N events
   *
   * @param nth
   *   Interval at which to take snapshots
   */
  def every[S, E](nth: Int): SnapshotCriteria[S, E] =
    CountBased[S, E](nth)
}

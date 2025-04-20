package com.github.j5ik2o.pekko.persistence.effector.javadsl

import scala.jdk.CollectionConverters.*
import com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Criteria for taking snapshots in Java API. This trait defines when snapshots should be taken.
 *
 * @tparam S
 *   Type of state
 * @tparam E
 *   Type of event
 */
sealed trait SnapshotCriteria[S, E] {

  /**
   * Determine whether a snapshot should be taken.
   *
   * @param event
   *   Event that was just persisted
   * @param state
   *   Current state
   * @param sequenceNumber
   *   Current sequence number
   * @return
   *   true if a snapshot should be taken, false otherwise
   */
  def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean

  /**
   * Convert this Java SnapshotCriteria to its Scala equivalent.
   *
   * @return
   *   Scala version of this SnapshotCriteria
   */
  def toScala: scaladsl.SnapshotCriteria[S, E]
}

/**
 * Companion object for SnapshotCriteria. Provides factory methods to create SnapshotCriteria
 * instances.
 */
object SnapshotCriteria {

  /**
   * Functional interface for a function that takes three arguments and returns a result. Used for
   * event-based snapshot criteria.
   *
   * @tparam T1
   *   Type of first argument
   * @tparam T2
   *   Type of second argument
   * @tparam T3
   *   Type of third argument
   * @tparam R
   *   Type of result
   */
  @FunctionalInterface
  trait TriFunction[T1, T2, T3, R] {

    /**
     * Apply the function to the arguments.
     *
     * @param t1
     *   First argument
     * @param t2
     *   Second argument
     * @param t3
     *   Third argument
     * @return
     *   Result
     */
    def apply(t1: T1, t2: T2, t3: T3): R
  }

  /**
   * Event-based snapshot criteria implementation. Takes a snapshot when the predicate returns true.
   *
   * @param predicate
   *   Function that determines whether to take a snapshot
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  private[effector] final case class JEventBased[S, E](
    predicate: TriFunction[E, S, Long, Boolean],
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      predicate.apply(event, state, sequenceNumber)

    override def toScala: scaladsl.SnapshotCriteria[S, E] =
      scaladsl.SnapshotCriteria.EventBased((e: E, s: S, l: Long) => predicate.apply(e, s, l))
  }

  /**
   * Count-based snapshot criteria implementation. Takes a snapshot every N events.
   *
   * @param every
   *   Number of events after which a snapshot should be taken
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  private[effector] final case class JCountBased[S, E](
    every: Int,
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      sequenceNumber % every == 0

    override def toScala: scaladsl.SnapshotCriteria[S, E] =
      scaladsl.SnapshotCriteria.CountBased(every)
  }

  /**
   * Combined snapshot criteria implementation. Combines multiple criteria with AND or OR logic.
   *
   * @param criteria
   *   List of criteria to combine
   * @param requireAll
   *   If true, all criteria must return true (AND), otherwise any criteria returning true is
   *   sufficient (OR)
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  private[effector] final case class JCombined[S, E](
    criteria: java.util.List[SnapshotCriteria[S, E]],
    requireAll: Boolean,
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean = {
      val results = criteria.asScala.map(_.shouldTakeSnapshot(event, state, sequenceNumber))
      if (requireAll) results.forall(identity) else results.exists(identity)
    }

    override def toScala: scaladsl.SnapshotCriteria[S, E] =
      scaladsl.SnapshotCriteria.Combined(
        criteria.asScala.map(_.toScala).toSeq,
        requireAll,
      )
  }

  /**
   * Create an event-based snapshot criteria. Takes a snapshot when the predicate returns true.
   *
   * @param predicate
   *   Function that determines whether to take a snapshot
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @return
   *   SnapshotCriteria instance
   */
  def eventBased[S, E](predicate: TriFunction[E, S, Long, Boolean]): SnapshotCriteria[S, E] =
    JEventBased(predicate)

  /**
   * Create a count-based snapshot criteria. Takes a snapshot every N events.
   *
   * @param every
   *   Number of events after which a snapshot should be taken
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @return
   *   SnapshotCriteria instance
   */
  def countBased[S, E](every: Int): SnapshotCriteria[S, E] = {
    require(every > 0, "every must be greater than 0")
    JCountBased(every)
  }

  /**
   * Create a combined snapshot criteria. Combines multiple criteria with AND or OR logic.
   *
   * @param criteria
   *   List of criteria to combine
   * @param requireAll
   *   If true, all criteria must return true (AND), otherwise any criteria returning true is
   *   sufficient (OR)
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @return
   *   SnapshotCriteria instance
   */
  def combined[S, E](
    criteria: java.util.List[SnapshotCriteria[S, E]],
    requireAll: Boolean): SnapshotCriteria[S, E] =
    JCombined(criteria, requireAll)

  /**
   * Create a snapshot criteria that always takes a snapshot.
   *
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @return
   *   SnapshotCriteria instance
   */
  def always[S, E](): SnapshotCriteria[S, E] =
    eventBased((unused1: E, unused2: S, unused3: Long) => true)

  /**
   * Create a snapshot criteria that takes a snapshot when the event is of a specific type.
   *
   * @param eventClass
   *   Class of the event type
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @return
   *   SnapshotCriteria instance
   */
  def onEventType[S, E](eventClass: Class[?]): SnapshotCriteria[S, E] =
    eventBased((evt: E, unused: S, unused2: Long) => eventClass.isInstance(evt))

  /**
   * Create a snapshot criteria that takes a snapshot every N events. Alias for countBased.
   *
   * @param nth
   *   Number of events after which a snapshot should be taken
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @return
   *   SnapshotCriteria instance
   */
  def every[S, E](nth: Int): SnapshotCriteria[S, E] =
    countBased(nth)

  /**
   * Convert a Scala SnapshotCriteria to its Java equivalent.
   *
   * @param criteria
   *   Scala SnapshotCriteria
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @return
   *   Java version of the SnapshotCriteria
   */
  def fromScala[S, E](criteria: scaladsl.SnapshotCriteria[S, E]): SnapshotCriteria[S, E] =
    criteria match {
      case scaladsl.SnapshotCriteria.EventBased(pred) =>
        eventBased((e: E, s: S, l: Long) => pred(e, s, l))
      case scaladsl.SnapshotCriteria.CountBased(n) =>
        countBased(n)
      case scaladsl.SnapshotCriteria.Combined(c, r) =>
        combined(c.map(fromScala[S, E]).asJava, r)
    }
}

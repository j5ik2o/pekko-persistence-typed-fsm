package com.github.j5ik2o.pekko.persistence.effector.javadsl

import scala.jdk.CollectionConverters.*

/**
 * Base trait for all message wrappers in Java API. This trait is used to mark messages that can be
 * used with the PersistenceEffector.
 *
 * @tparam M
 *   Type of message
 */
trait MessageWrapper[M]

/**
 * Trait for messages containing persisted events in Java API. This trait is used to wrap events
 * that have been persisted.
 *
 * @tparam E
 *   Type of event
 * @tparam M
 *   Type of message
 */
trait PersistedEvent[E, M] extends MessageWrapper[M] {

  /**
   * Get the persisted events.
   *
   * @return
   *   List of events
   */
  def events: java.util.List[E]
}

/**
 * Trait for messages containing persisted state in Java API. This trait is used to wrap state that
 * has been persisted as a snapshot.
 *
 * @tparam S
 *   Type of state
 * @tparam M
 *   Type of message
 */
trait PersistedState[S, M] extends MessageWrapper[M] {

  /**
   * Get the persisted state.
   *
   * @return
   *   State
   */
  def state: S
}

/**
 * Trait for messages containing recovered state in Java API. This trait is used to wrap state that
 * has been recovered from events or snapshots.
 *
 * @tparam S
 *   Type of state
 * @tparam M
 *   Type of message
 */
trait RecoveredState[S, M] extends MessageWrapper[M] {

  /**
   * Get the recovered state.
   *
   * @return
   *   State
   */
  def state: S
}

/**
 * Trait for messages containing information about deleted snapshots in Java API. This trait is used
 * to wrap information about snapshots that have been deleted.
 *
 * @tparam M
 *   Type of message
 */
trait DeletedSnapshots[M] extends MessageWrapper[M] {

  /**
   * Get the maximum sequence number of deleted snapshots.
   *
   * @return
   *   Maximum sequence number
   */
  def maxSequenceNumber: Long
}

/**
 * Adapter to convert the JavaDSL version of MessageWrapper to the ScalaDSL version. This object
 * provides adapter classes to convert between Java and Scala message wrappers.
 */
object MessageWrapperAdapter {

  /**
   * Adapter to convert JavaDSL version of PersistedEvent to ScalaDSL version.
   *
   * @param javaEvent
   *   Java persisted event
   * @tparam E
   *   Type of event
   * @tparam M
   *   Type of message
   */
  private[effector] class JavaPersistedEventAdapter[E, M](javaEvent: PersistedEvent[E, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M] {
    override def events: Seq[E] = javaEvent.events.asScala.toSeq
  }

  /**
   * Adapter to convert the JavaDSL version of PersistedState to ScalaDSL version.
   *
   * @param javaState
   *   Java persisted state
   * @tparam S
   *   Type of state
   * @tparam M
   *   Type of message
   */
  private[effector] class JavaPersistedStateAdapter[S, M](javaState: PersistedState[S, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] {
    override def state: S = javaState.state
  }

  /**
   * Adapter to convert the JavaDSL version of RecoveredState to the ScalaDSL version.
   *
   * @param javaState
   *   Java recovered state
   * @tparam S
   *   Type of state
   * @tparam M
   *   Type of message
   */
  private[effector] class JavaRecoveredStateAdapter[S, M](javaState: RecoveredState[S, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] {
    override def state: S = javaState.state
  }

  /**
   * Adapter to convert the JavaDSL version of DeletedSnapshots to the ScalaDSL version.
   *
   * @param javaSnapshots
   *   Java deleted snapshots
   * @tparam M
   *   Type of message
   */
  private[effector] class JavaDeletedSnapshotsAdapter[M](javaSnapshots: DeletedSnapshots[M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] {
    override def maxSequenceNumber: Long = javaSnapshots.maxSequenceNumber
  }
}

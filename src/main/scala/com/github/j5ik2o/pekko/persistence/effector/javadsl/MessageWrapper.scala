package com.github.j5ik2o.pekko.persistence.effector.javadsl

import scala.jdk.CollectionConverters.*

/**
 * MessageWrapper trait for JavaDSL
 */
trait MessageWrapper[M]

/**
 * PersistedEvent trait for JavaDSL
 */
trait PersistedEvent[E, M] extends MessageWrapper[M] {
  def events: java.util.List[E]
}

/**
 * PersistedState trait for JavaDSL
 */
trait PersistedState[S, M] extends MessageWrapper[M] {
  def state: S
}

/**
 * RecoveredState trait for JavaDSL
 */
trait RecoveredState[S, M] extends MessageWrapper[M] {
  def state: S
}

/**
 * DeletedSnapshots trait for JavaDSL
 */
trait DeletedSnapshots[M] extends MessageWrapper[M] {
  def maxSequenceNumber: Long
}

/**
 * Adapter to convert JavaDSL version of MessageWrapper to ScalaDSL version
 */
object MessageWrapperAdapter {
  // Adapter to convert JavaDSL version of PersistedEvent to ScalaDSL version
  private[effector] class JavaPersistedEventAdapter[E, M](javaEvent: PersistedEvent[E, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M] {
    override def events: Seq[E] = javaEvent.events.asScala.toSeq
  }

  // Adapter to convert JavaDSL version of PersistedState to ScalaDSL version
  private[effector] class JavaPersistedStateAdapter[S, M](javaState: PersistedState[S, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] {
    override def state: S = javaState.state
  }

  // Adapter to convert JavaDSL version of RecoveredState to ScalaDSL version
  private[effector] class JavaRecoveredStateAdapter[S, M](javaState: RecoveredState[S, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] {
    override def state: S = javaState.state
  }

  // Adapter to convert JavaDSL version of DeletedSnapshots to ScalaDSL version
  private[effector] class JavaDeletedSnapshotsAdapter[M](javaSnapshots: DeletedSnapshots[M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] {
    override def maxSequenceNumber: Long = javaSnapshots.maxSequenceNumber
  }
}

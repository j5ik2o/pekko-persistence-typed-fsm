package com.github.j5ik2o.pekko.persistence.effector.javadsl

import scala.jdk.CollectionConverters.*

/**
 * JavaDSL用のMessageWrapperトレイト
 */
trait MessageWrapper[M]

/**
 * JavaDSL用のPersistedEventトレイト
 */
trait PersistedEvent[E, M] extends MessageWrapper[M] {
  def events: java.util.List[E]
}

/**
 * JavaDSL用のPersistedStateトレイト
 */
trait PersistedState[S, M] extends MessageWrapper[M] {
  def state: S
}

/**
 * JavaDSL用のRecoveredStateトレイト
 */
trait RecoveredState[S, M] extends MessageWrapper[M] {
  def state: S
}

/**
 * JavaDSL用のDeletedSnapshotsトレイト
 */
trait DeletedSnapshots[M] extends MessageWrapper[M] {
  def maxSequenceNumber: Long
}

/**
 * JavaDSL版のMessageWrapperをScalaDSL版に変換するためのアダプター
 */
object MessageWrapperAdapter {
  // JavaDSL版のPersistedEventをScalaDSL版に変換するためのアダプター
  class JavaPersistedEventAdapter[E, M](javaEvent: PersistedEvent[E, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M] {
    override def events: Seq[E] = javaEvent.events.asScala.toSeq
  }

  // JavaDSL版のPersistedStateをScalaDSL版に変換するためのアダプター
  class JavaPersistedStateAdapter[S, M](javaState: PersistedState[S, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] {
    override def state: S = javaState.state
  }

  // JavaDSL版のRecoveredStateをScalaDSL版に変換するためのアダプター
  class JavaRecoveredStateAdapter[S, M](javaState: RecoveredState[S, M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] {
    override def state: S = javaState.state
  }

  // JavaDSL版のDeletedSnapshotsをScalaDSL版に変換するためのアダプター
  class JavaDeletedSnapshotsAdapter[M](javaSnapshots: DeletedSnapshots[M])
    extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] {
    override def maxSequenceNumber: Long = javaSnapshots.maxSequenceNumber
  }
}

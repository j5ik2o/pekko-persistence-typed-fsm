package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageWrapper as SMessageWrapper
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent as SPersistedEvent

import scala.jdk.CollectionConverters.*

trait MessageWrapper[M] {
  def toScala: SMessageWrapper[M]
}

trait PersistedEvent[E, M] extends MessageWrapper[M] {
  def events: java.util.List[E]
  def toScala: SPersistedEvent[E, M] = {
    val javaEvents = events
    new JavaPersistedEvent[E, M](javaEvents)
  }
}

private class JavaPersistedEvent[E, M](javaEvents: java.util.List[E]) extends SPersistedEvent[E, M] {
  override def events: Seq[E] = javaEvents.asScala.toSeq
}

trait PersistedState[S, M] extends MessageWrapper[M] {
  def state: S
  def toScala: com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] = {
    val stateValue = state
    new JavaPersistedState[S, M](stateValue)
  }
}

private class JavaPersistedState[S, M](stateValue: S) extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] {
  override def state: S = stateValue
}

trait RecoveredState[S, M] extends MessageWrapper[M] {
  def state: S
  def toScala: com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] = {
    val stateValue = state
    new JavaRecoveredState[S, M](stateValue)
  }
}

private class JavaRecoveredState[S, M](stateValue: S) extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] {
  override def state: S = stateValue
}

trait DeletedSnapshots[M] extends MessageWrapper[M] {
  def maxSequenceNumber: Long
  def toScala: com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] = {
    val maxSeqNum = maxSequenceNumber
    new JavaDeletedSnapshots[M](maxSeqNum)
  }
}

private class JavaDeletedSnapshots[M](maxSeqNum: Long) extends com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] {
  override def maxSequenceNumber: Long = maxSeqNum
}

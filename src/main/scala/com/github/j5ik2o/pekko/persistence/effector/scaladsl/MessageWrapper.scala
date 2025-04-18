package com.github.j5ik2o.pekko.persistence.effector.scaladsl

trait MessageWrapper[M]

trait PersistedEvent[E, M] extends MessageWrapper[M] {
  def events: Seq[E]
}

trait PersistedState[S, M] extends MessageWrapper[M] {
  def state: S
}

trait RecoveredState[S, M] extends MessageWrapper[M] {
  def state: S
}

trait DeletedSnapshots[M] extends MessageWrapper[M] {
  def maxSequenceNumber: Long
}

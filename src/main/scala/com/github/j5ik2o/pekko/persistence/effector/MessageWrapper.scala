package com.github.j5ik2o.pekko.persistence.effector

sealed trait MessageWrapper[M] { self: M => }

trait PersistedEvent[E, M] extends MessageWrapper[M] { self: M =>
  def events: Seq[E]
}

trait PersistedState[S, M] extends MessageWrapper[M] { self: M =>
  def state: S
}

trait RecoveredState[S, M] extends MessageWrapper[M] { self: M =>
  def state: S
}

/**
 * スナップショット削除の応答をラップするトレイト
 */
trait DeletedSnapshots[M] extends MessageWrapper[M] { self: M =>
  def maxSequenceNumber: Long
}

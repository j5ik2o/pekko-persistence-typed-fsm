package com.github.j5ik2o.eff.sm.splitter

sealed trait MessageWrapper[M] { self: M => }

trait PersistedEvent[E, M] extends MessageWrapper[M] { self: M =>
  def events: Seq[E]
}

trait RecoveredState[S, M] extends MessageWrapper[M] { self: M =>
  def state: S
}

package com.github.j5ik2o.pekko.persistence.typed.fsm

sealed trait MessageWrapper[M] { self: M => }

trait PersistedEvent[E, M] extends MessageWrapper[M] { self: M =>
  def events: Seq[E]
}

trait RecoveredState[S, M] extends MessageWrapper[M] { self: M =>
  def state: S
}

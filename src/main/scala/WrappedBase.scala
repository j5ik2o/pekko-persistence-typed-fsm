package com.github.j5ik2o.eff.sm.splitter

sealed trait WrappedBase[M] { self: M => }

trait WrappedPersisted[E, M] extends WrappedBase[M] { self: M =>
  def events: Seq[E]
}

trait WrappedRecovered[S, M] extends WrappedBase[M] { self: M =>
  def state: S
}

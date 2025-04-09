package com.github.j5ik2o.eff.sm.splitter

trait WrappedSupportProtocol[S, E] {
  type Message <: Matchable
  def wrappedISO: WrappedISO[S, E, Message]
}

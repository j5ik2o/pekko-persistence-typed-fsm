package com.github.j5ik2o.eff.sm.splitter

trait MessageProtocol[S, E] {
  type Message <: Matchable
  def messageConverter: MessageConverter[S, E, Message]
}

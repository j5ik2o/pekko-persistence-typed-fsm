package com.github.j5ik2o.pekko.persistence.effector.scaladsl

trait MessageProtocol[S, E] {
  type Message <: Matchable
  def messageConverter: MessageConverter[S, E, Message]
}

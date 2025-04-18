package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageConverter

trait MessageProtocol[S, E, M] {
  def messageConverter: MessageConverter[S, E, M]
}

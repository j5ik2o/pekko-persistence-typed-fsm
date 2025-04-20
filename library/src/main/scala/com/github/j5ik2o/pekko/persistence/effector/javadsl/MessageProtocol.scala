package com.github.j5ik2o.pekko.persistence.effector.javadsl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageConverter

/**
 * Trait for message protocol in Java API. This trait defines the protocol for converting between
 * domain events/states and messages.
 *
 * @tparam S
 *   Type of state
 * @tparam E
 *   Type of event
 * @tparam M
 *   Type of message
 */
trait MessageProtocol[S, E, M] {

  /**
   * Get the message converter for this protocol. The message converter is used to convert between
   * domain events/states and messages.
   *
   * @return
   *   Message converter
   */
  def messageConverter: MessageConverter[S, E, M]
}

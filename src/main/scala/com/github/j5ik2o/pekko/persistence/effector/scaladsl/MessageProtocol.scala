package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Trait defining the message protocol for communication between components.
 * This trait provides a way to convert between domain events/states and messages.
 *
 * @tparam S Type of state
 * @tparam E Type of event
 */
trait MessageProtocol[S, E] {
  /**
   * Type of message used for communication.
   * Must be a matchable type to support pattern matching.
   */
  type Message <: Matchable
  
  /**
   * Get the message converter for this protocol.
   *
   * @return MessageConverter instance for converting between domain events/states and messages
   */
  def messageConverter: MessageConverter[S, E, Message]
}

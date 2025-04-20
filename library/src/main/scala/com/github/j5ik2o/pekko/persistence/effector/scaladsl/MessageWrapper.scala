package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Base trait for all message wrappers.
 *
 * @tparam M
 *   Type of message
 */
trait MessageWrapper[M]

/**
 * Trait for messages containing persisted events.
 *
 * @tparam E
 *   Type of event
 * @tparam M
 *   Type of message
 */
trait PersistedEvent[E, M] extends MessageWrapper[M] {

  /**
   * Get the persisted events.
   *
   * @return
   *   Sequence of events
   */
  def events: Seq[E]
}

/**
 * Trait for messages containing persisted state.
 *
 * @tparam S
 *   Type of state
 * @tparam M
 *   Type of message
 */
trait PersistedState[S, M] extends MessageWrapper[M] {

  /**
   * Get the persisted state.
   *
   * @return
   *   State
   */
  def state: S
}

/**
 * Trait for messages containing recovered state.
 *
 * @tparam S
 *   Type of state
 * @tparam M
 *   Type of message
 */
trait RecoveredState[S, M] extends MessageWrapper[M] {

  /**
   * Get the recovered state.
   *
   * @return
   *   State
   */
  def state: S
}

/**
 * Trait for messages containing information about deleted snapshots.
 *
 * @tparam M
 *   Type of message
 */
trait DeletedSnapshots[M] extends MessageWrapper[M] {

  /**
   * Get the maximum sequence number of deleted snapshots.
   *
   * @return
   *   Maximum sequence number
   */
  def maxSequenceNumber: Long
}

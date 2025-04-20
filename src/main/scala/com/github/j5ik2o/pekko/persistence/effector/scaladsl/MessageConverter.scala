package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/**
 * Trait for converting between domain events/states and messages.
 *
 * @tparam S
 *   Type of state
 * @tparam E
 *   Type of event
 * @tparam M
 *   Type of message
 */
trait MessageConverter[S, E, M] {

  /**
   * Wrap persisted events into a message.
   *
   * @param events
   *   Events to wrap
   * @return
   *   Message containing the events
   */
  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]

  /**
   * Wrap a persisted snapshot into a message.
   *
   * @param state
   *   State to wrap
   * @return
   *   Message containing the state
   */
  def wrapPersistedSnapshot(state: S): M & PersistedState[S, M]

  /**
   * Wrap a recovered state into a message.
   *
   * @param state
   *   State to wrap
   * @return
   *   Message containing the state
   */
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]

  /**
   * Wrap deleted snapshots information into a message.
   *
   * @param maxSequenceNumber
   *   Maximum sequence number of deleted snapshots
   * @return
   *   Message containing the maximum sequence number
   */
  def wrapDeleteSnapshots(maxSequenceNumber: Long): M & DeletedSnapshots[M]

  /**
   * Extract persisted events from a message.
   *
   * @param message
   *   Message to extract from
   * @return
   *   Option containing the events, or None if the message doesn't contain events
   */
  def unwrapPersistedEvents(message: M): Option[Seq[E]] = message.asMatchable match {
    case msg: PersistedEvent[E, M] @unchecked => Some(msg.events)
    case other => None
  }

  /**
   * Extract persisted snapshot from a message.
   *
   * @param message
   *   Message to extract from
   * @return
   *   Option containing the state, or None if the message doesn't contain a snapshot
   */
  def unwrapPersistedSnapshot(message: M): Option[S] = message.asMatchable match {
    case msg: PersistedState[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

  /**
   * Extract recovered state from a message.
   *
   * @param message
   *   Message to extract from
   * @return
   *   Option containing the state, or None if the message doesn't contain a recovered state
   */
  def unwrapRecoveredState(message: M): Option[S] = message.asMatchable match {
    case msg: RecoveredState[S, M] @unchecked => Some(msg.state)
    case _ => None
  }

  /**
   * Extract deleted snapshots information from a message.
   *
   * @param message
   *   Message to extract from
   * @return
   *   Option containing the maximum sequence number, or None if the message doesn't contain deleted
   *   snapshots information
   */
  def unwrapDeleteSnapshots(message: M): Option[Long] = message.asMatchable match {
    case msg: DeletedSnapshots[M] @unchecked => Some(msg.maxSequenceNumber)
    case _ => None
  }

}

object MessageConverter {
  private final case class Default[S, E, M <: Matchable](
    _wrapPersistedEvents: Seq[E] => M & PersistedEvent[E, M],
    _wrapPersistedState: S => M & PersistedState[S, M],
    _wrapRecoveredState: S => M & RecoveredState[S, M],
    _wrapDeleteSnapshots: Long => M & DeletedSnapshots[M],
  ) extends MessageConverter[S, E, M] {
    override def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M] =
      _wrapPersistedEvents(events)
    override def wrapPersistedSnapshot(state: S): M & PersistedState[S, M] = _wrapPersistedState(
      state)
    override def wrapRecoveredState(state: S): M & RecoveredState[S, M] = _wrapRecoveredState(state)
    override def wrapDeleteSnapshots(maxSequenceNumber: Long): M & DeletedSnapshots[M] =
      _wrapDeleteSnapshots(maxSequenceNumber)
  }

  /**
   * Create a MessageConverter with the specified functions.
   *
   * @param wrapPersistedEvents
   *   Function to wrap persisted events
   * @param wrapPersistedState
   *   Function to wrap persisted state
   * @param wrapRecoveredState
   *   Function to wrap recovered state
   * @param wrapDeleteSnapshots
   *   Function to wrap deleted snapshots information
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @tparam M
   *   Type of message
   * @return
   *   MessageConverter instance
   */
  def apply[S, E, M <: Matchable](
    wrapPersistedEvents: Seq[E] => M & PersistedEvent[E, M],
    wrapPersistedState: S => M & PersistedState[S, M],
    wrapRecoveredState: S => M & RecoveredState[S, M],
    wrapDeleteSnapshots: Long => M & DeletedSnapshots[M],
  ): MessageConverter[S, E, M] =
    Default(wrapPersistedEvents, wrapPersistedState, wrapRecoveredState, wrapDeleteSnapshots)

  private[effector] case class StandardPersistedEvent[E](events: Seq[E])
    extends PersistedEvent[E, Any]

  private[effector] case class StandardPersistedState[S](state: S) extends PersistedState[S, Any]

  private[effector] case class StandardRecoveredState[S](state: S) extends RecoveredState[S, Any]

  private[effector] case class StandardDeletedSnapshots(maxSequenceNumber: Long)
    extends DeletedSnapshots[Any]

  /**
   * Create a default MessageConverter with standard implementations.
   *
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @tparam M
   *   Type of message
   * @return
   *   Default MessageConverter instance
   */
  def defaultFunctions[S, E, M]: MessageConverter[S, E, M] = new MessageConverter[S, E, M] {
    override def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M] =
      StandardPersistedEvent(events).asInstanceOf[M & PersistedEvent[E, M]]

    override def wrapPersistedSnapshot(state: S): M & PersistedState[S, M] =
      StandardPersistedState(state).asInstanceOf[M & PersistedState[S, M]]

    override def wrapRecoveredState(state: S): M & RecoveredState[S, M] =
      StandardRecoveredState(state).asInstanceOf[M & RecoveredState[S, M]]

    override def wrapDeleteSnapshots(maxSequenceNumber: Long): M & DeletedSnapshots[M] =
      StandardDeletedSnapshots(maxSequenceNumber).asInstanceOf[M & DeletedSnapshots[M]]
  }

}

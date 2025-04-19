package com.github.j5ik2o.pekko.persistence.effector.javadsl

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/**
 * Trait for converting between domain events/states and messages in Java API.
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
  def wrapPersistedEvents(events: java.util.List[E]): M & PersistedEvent[E, M]

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
  def wrapDeleteSnapshots(maxSequenceNumber: java.lang.Long): M & DeletedSnapshots[M]

  /**
   * Extract persisted events from a message.
   *
   * @param message
   *   Message to extract from
   * @return
   *   Option containing the events, or None if the message doesn't contain events
   */
  def unwrapPersistedEvents(message: M): Option[java.util.List[E]] = message.asMatchable match {
    case msg: PersistedEvent[E, M] @unchecked => Some(msg.events)
    case _ => None
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
  def unwrapDeleteSnapshots(message: M): Option[java.lang.Long] = message.asMatchable match {
    case msg: DeletedSnapshots[M] @unchecked => Some(msg.maxSequenceNumber)
    case _ => None
  }

  /**
   * Convert this Java MessageConverter to its Scala equivalent.
   *
   * @return
   *   Scala version of this MessageConverter
   */
  def toScala: com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageConverter[S, E, M] = {
    val self = this
    new com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageConverter[S, E, M] {
      override def wrapPersistedEvents(events: Seq[E]): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M] = {
        val javaEvents = events.asJava
        val result = self.wrapPersistedEvents(javaEvents)
        // Create adapter to convert JavaDSL version of PersistedEvent to ScalaDSL version
        val adapter = new MessageWrapperAdapter.JavaPersistedEventAdapter[E, M](result)
        // Return the original message combined with the adapter
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent[E, M]]
      }

      override def unwrapPersistedEvents(message: M): Option[Seq[E]] =
        self.unwrapPersistedEvents(message).map(_.asScala.toSeq)

      override def wrapPersistedSnapshot(state: S): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M] = {
        val result = self.wrapPersistedSnapshot(state)
        // Create adapter to convert JavaDSL version of PersistedState to ScalaDSL version
        val adapter = new MessageWrapperAdapter.JavaPersistedStateAdapter[S, M](result)
        // Return the original message combined with the adapter
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState[S, M]]
      }

      override def unwrapPersistedSnapshot(message: M): Option[S] =
        self.unwrapPersistedSnapshot(message)

      override def wrapRecoveredState(state: S): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M] = {
        val result = self.wrapRecoveredState(state)
        // Create adapter to convert JavaDSL version of RecoveredState to ScalaDSL version
        val adapter = new MessageWrapperAdapter.JavaRecoveredStateAdapter[S, M](result)
        // Return the original message combined with the adapter
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState[S, M]]
      }

      override def unwrapRecoveredState(message: M): Option[S] =
        self.unwrapRecoveredState(message)

      override def wrapDeleteSnapshots(maxSequenceNumber: Long): M &
        com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M] = {
        val javaLong = maxSequenceNumber.asInstanceOf[java.lang.Long]
        val result = self.wrapDeleteSnapshots(javaLong)
        // Create adapter to convert JavaDSL version of DeletedSnapshots to ScalaDSL version
        val adapter = new MessageWrapperAdapter.JavaDeletedSnapshotsAdapter[M](result)
        // Return the original message combined with the adapter
        result.asInstanceOf[M &
          com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots[M]]
      }

      override def unwrapDeleteSnapshots(message: M): Option[Long] =
        self.unwrapDeleteSnapshots(message).map(_.asInstanceOf[Long])
    }
  }
}

/**
 * Companion object for MessageConverter. Provides factory methods to create MessageConverter
 * instances.
 */
object MessageConverter {
  private final case class Default[S, E, M <: Matchable](
    _wrapPersistedEvents: java.util.function.Function[java.util.List[E], M & PersistedEvent[E, M]],
    _wrapPersistedState: java.util.function.Function[S, M & PersistedState[S, M]],
    _wrapRecoveredState: java.util.function.Function[S, M & RecoveredState[S, M]],
    _wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M & DeletedSnapshots[M]],
  ) extends MessageConverter[S, E, M] {
    override def wrapPersistedEvents(events: java.util.List[E]): M & PersistedEvent[E, M] =
      _wrapPersistedEvents(events)

    override def wrapPersistedSnapshot(state: S): M & PersistedState[S, M] = _wrapPersistedState(
      state)

    override def wrapRecoveredState(state: S): M & RecoveredState[S, M] = _wrapRecoveredState(state)

    override def wrapDeleteSnapshots(maxSequenceNumber: java.lang.Long): M & DeletedSnapshots[M] =
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
  def create[S, E, M <: Matchable](
    wrapPersistedEvents: java.util.function.Function[java.util.List[E], M & PersistedEvent[E, M]],
    wrapPersistedState: java.util.function.Function[S, M & PersistedState[S, M]],
    wrapRecoveredState: java.util.function.Function[S, M & RecoveredState[S, M]],
    wrapDeleteSnapshots: java.util.function.Function[java.lang.Long, M & DeletedSnapshots[M]],
  ): MessageConverter[S, E, M] =
    Default(wrapPersistedEvents, wrapPersistedState, wrapRecoveredState, wrapDeleteSnapshots)

  // Standard message wrapper class (consider moving to internal package)
  private[effector] class StandardJavaPersistedEvent[E](val events: java.util.List[E])
    extends PersistedEvent[E, Any]

  private[effector] class StandardJavaPersistedState[S](val state: S) extends PersistedState[S, Any]

  private[effector] class StandardJavaRecoveredState[S](val state: S) extends RecoveredState[S, Any]

  private[effector] class StandardJavaDeletedSnapshots(val maxSequenceNumber: Long)
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
  def defaultFunctions[S, E, M]: MessageConverter[S, E, M] =
    new MessageConverter[S, E, M] {
      override def wrapPersistedEvents(events: java.util.List[E]): M & PersistedEvent[E, M] =
        new StandardJavaPersistedEvent[E](events).asInstanceOf[M & PersistedEvent[E, M]]

      override def wrapPersistedSnapshot(state: S): M & PersistedState[S, M] =
        new StandardJavaPersistedState[S](state).asInstanceOf[M & PersistedState[S, M]]

      override def wrapRecoveredState(state: S): M & RecoveredState[S, M] =
        new StandardJavaRecoveredState[S](state).asInstanceOf[M & RecoveredState[S, M]]

      override def wrapDeleteSnapshots(maxSequenceNumber: java.lang.Long): M & DeletedSnapshots[M] =
        new StandardJavaDeletedSnapshots(maxSequenceNumber).asInstanceOf[M & DeletedSnapshots[M]]
    }
}

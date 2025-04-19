package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

/**
 * Singleton object for storing in-memory events and snapshots
 */
private[effector] object InMemoryEventStore {

  import scala.jdk.CollectionConverters.*

  // Use thread-safe collections
  // persistenceId -> event list
  private val events: scala.collection.mutable.Map[String, Vector[Any]] =
    new java.util.concurrent.ConcurrentHashMap[String, Vector[Any]]().asScala
  // persistenceId -> latest snapshot
  private val snapshots: scala.collection.mutable.Map[String, Any] =
    new java.util.concurrent.ConcurrentHashMap[String, Any]().asScala
  // persistenceId -> latest event index at snapshot save time
  private val snapshotEventIndices: scala.collection.mutable.Map[String, Int] =
    new java.util.concurrent.ConcurrentHashMap[String, Int]().asScala
  // persistenceId -> current sequence number
  private val sequenceNumbers: scala.collection.mutable.Map[String, Long] =
    new java.util.concurrent.ConcurrentHashMap[String, Long]().asScala

  /**
   * Add a single event to the store.
   *
   * @param id
   *   Persistence ID
   * @param event
   *   Event to add
   * @tparam E
   *   Event type
   */
  def addEvent[E](id: String, event: E): Unit = {
    events.updateWith(id) {
      case Some(existing) => Some(existing :+ event)
      case None => Some(Vector(event))
    }
    val currentSeq = sequenceNumbers.getOrElse(id, 0L)
    sequenceNumbers.update(id, currentSeq + 1)
  }

  /**
   * Add multiple events to the store.
   *
   * @param id
   *   Persistence ID
   * @param newEvents
   *   Events to add
   * @tparam E
   *   Event type
   */
  def addEvents[E](id: String, newEvents: Seq[E]): Unit = {
    events.updateWith(id) {
      case Some(existing) => Some(existing ++ newEvents)
      case None => Some(newEvents.toVector)
    }
    val currentSeq = sequenceNumbers.getOrElse(id, 0L)
    sequenceNumbers.update(id, currentSeq + newEvents.size)
  }

  /**
   * Get the current sequence number for a persistence ID.
   *
   * @param id
   *   Persistence ID
   * @return
   *   Current sequence number
   */
  def getCurrentSequenceNumber(id: String): Long =
    sequenceNumbers.getOrElse(id, 0L)

  /**
   * Save a snapshot for a persistence ID.
   *
   * @param id
   *   Persistence ID
   * @param snapshot
   *   Snapshot to save
   * @tparam S
   *   Snapshot type
   */
  def saveSnapshot[S](id: String, snapshot: S): Unit = {
    snapshots.update(id, snapshot)
    // Record the number of events at the time of snapshot save
    snapshotEventIndices.update(id, events.getOrElse(id, Vector.empty).size)
  }

  /**
   * Get all events for a persistence ID.
   *
   * @param id
   *   Persistence ID
   * @tparam E
   *   Event type
   * @return
   *   Vector of events
   */
  def getEvents[E](id: String): Vector[E] =
    events.getOrElse(id, Vector.empty).asInstanceOf[Vector[E]]

  /**
   * Get the latest snapshot for a persistence ID.
   *
   * @param id
   *   Persistence ID
   * @tparam S
   *   Snapshot type
   * @return
   *   Option containing the latest snapshot, or None if no snapshot exists
   */
  def getLatestSnapshot[S](id: String): Option[S] =
    snapshots.get(id).map(_.asInstanceOf[S])

  /**
   * Get only events that occurred after the latest snapshot.
   *
   * @param id
   *   Persistence ID
   * @tparam E
   *   Event type
   * @return
   *   Vector of events after the latest snapshot
   */
  def getEventsAfterSnapshot[E](id: String): Vector[E] = {
    val allEvents = getEvents[E](id)
    if (snapshots.contains(id) && allEvents.nonEmpty) {
      // If snapshot exists and events also exist
      // return only events after the last snapshot was created
      val snapshotIndex = snapshotEventIndices.getOrElse(id, 0)
      allEvents.drop(snapshotIndex)
    } else {
      allEvents
    }
  }

  /**
   * Replay events to rebuild state.
   *
   * @param id
   *   Persistence ID
   * @param state
   *   Initial state
   * @param applyEvent
   *   Function to apply an event to a state
   * @tparam S
   *   State type
   * @tparam E
   *   Event type
   * @return
   *   Updated state after applying all events
   */
  def replayEvents[S, E](id: String, state: S, applyEvent: (S, E) => S): S =
    getEventsAfterSnapshot[E](id).foldLeft(state)(applyEvent)

  /**
   * Clear all data from the store. This method is primarily used for testing.
   */
  def clear(): Unit = {
    events.clear()
    snapshots.clear()
    snapshotEventIndices.clear()
    sequenceNumbers.clear()
  }
}

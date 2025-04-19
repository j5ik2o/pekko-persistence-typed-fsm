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

  def addEvent[E](id: String, event: E): Unit = {
    events.updateWith(id) {
      case Some(existing) => Some(existing :+ event)
      case None => Some(Vector(event))
    }
    val currentSeq = sequenceNumbers.getOrElse(id, 0L)
    sequenceNumbers.update(id, currentSeq + 1)
  }

  def addEvents[E](id: String, newEvents: Seq[E]): Unit = {
    events.updateWith(id) {
      case Some(existing) => Some(existing ++ newEvents)
      case None => Some(newEvents.toVector)
    }
    val currentSeq = sequenceNumbers.getOrElse(id, 0L)
    sequenceNumbers.update(id, currentSeq + newEvents.size)
  }

  def getCurrentSequenceNumber(id: String): Long =
    sequenceNumbers.getOrElse(id, 0L)

  def saveSnapshot[S](id: String, snapshot: S): Unit = {
    snapshots.update(id, snapshot)
    // Record the number of events at the time of snapshot save
    snapshotEventIndices.update(id, events.getOrElse(id, Vector.empty).size)
  }

  def getEvents[E](id: String): Vector[E] =
    events.getOrElse(id, Vector.empty).asInstanceOf[Vector[E]]

  def getLatestSnapshot[S](id: String): Option[S] =
    snapshots.get(id).map(_.asInstanceOf[S])

  // Get only events after snapshot
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

  def replayEvents[S, E](id: String, state: S, applyEvent: (S, E) => S): S =
    getEventsAfterSnapshot[E](id).foldLeft(state)(applyEvent)

  // Method to clear the store for testing
  def clear(): Unit = {
    events.clear()
    snapshots.clear()
    snapshotEventIndices.clear()
    sequenceNumbers.clear()
  }
}

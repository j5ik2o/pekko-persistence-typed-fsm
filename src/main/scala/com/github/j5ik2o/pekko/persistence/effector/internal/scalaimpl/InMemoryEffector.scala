package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  RetentionCriteria,
}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, StashBuffer}

/**
 * In-memory implementation of PersistenceEffector.
 * This implementation stores events and snapshots in memory, making it suitable for testing
 * and scenarios where persistence is not required.
 *
 * @param ctx Actor context for the actor using this effector
 * @param stashBuffer Stash buffer for storing messages during persistence operations
 * @param config Configuration for the persistence effector
 * @tparam S Type of state
 * @tparam E Type of event
 * @tparam M Type of message
 */
private[effector] final class InMemoryEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
) extends PersistenceEffector[S, E, M] {
  import config.*

  // Restore initial state (snapshot + events) - similar role to receiveRecover in PersistentActor
  private val latestSnapshot = InMemoryEventStore.getLatestSnapshot[S](persistenceId)
  private var currentState: S = latestSnapshot match {
    case Some(snapshot) =>
      ctx.log.debug(s"Recovered from snapshot for $persistenceId")
      // Restore state from snapshot and apply subsequent events
      InMemoryEventStore.replayEvents(persistenceId, snapshot, applyEvent)
    case None =>
      ctx.log.debug(s"Starting from initial state for $persistenceId")
      // Apply events from initial state
      InMemoryEventStore.replayEvents(persistenceId, initialState, applyEvent)
  }

  // Get current sequence number
  private def getCurrentSequenceNumber: Long =
    InMemoryEventStore.getCurrentSequenceNumber(persistenceId)

  /**
   * Calculate the maximum sequence number of snapshots to be deleted based on RetentionCriteria
   *
   * @param currentSequenceNumber
   *   Current sequence number
   * @param retention
   *   Retention policy
   * @return
   *   Maximum sequence number of snapshots to be deleted (0 if there are no snapshots to delete)
   */
  private def calculateMaxSequenceNumberToDelete(
    currentSequenceNumber: Long,
    retention: RetentionCriteria,
  ): Long =
    // Calculate only if both snapshotEvery and keepNSnapshots are set
    (retention.snapshotEvery, retention.keepNSnapshots) match {
      case (Some(snapshotEvery), Some(keepNSnapshots)) =>
        // Calculate the sequence number of the latest snapshot
        val latestSnapshotSeqNr = currentSequenceNumber - (currentSequenceNumber % snapshotEvery)

        if (latestSnapshotSeqNr < snapshotEvery) {
          // If even the first snapshot has not been created
          0L
        } else {
          // The oldest sequence number of snapshots to keep
          val oldestKeptSnapshot =
            latestSnapshotSeqNr - (snapshotEvery.toLong * (keepNSnapshots - 1))

          if (oldestKeptSnapshot <= 0) {
            // If all snapshots to be kept do not exist
            0L
          } else {
            // Maximum sequence number to be deleted (snapshot just before oldestKeptSnapshot)
            val maxSequenceNumberToDelete = oldestKeptSnapshot - snapshotEvery

            if (maxSequenceNumberToDelete <= 0) 0L else maxSequenceNumberToDelete
          }
        }
      case _ =>
        // Do not delete if either setting is missing
        0L
    }

  // Emulate the persist method of PersistentActor
  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting event: {}", event)

    // Save event to memory
    // Note: Similar to the persist method of PersistentActor, it only saves the event
    // and does not update the state at this point
    InMemoryEventStore.addEvent(persistenceId, event)

    // Execute callback immediately (no waiting for persistence)
    // Command handler updates state within the callback
    val behavior = onPersisted(event)

    // unstashAll if stashBuffer is not empty
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  // Emulate the persistAll method of PersistentActor
  override def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting events: {}", events)

    // Save events to memory
    // Note: Similar to the persistAll method of PersistentActor, it only saves the events
    // and does not update the state at this point
    InMemoryEventStore.addEvents(persistenceId, events)

    // Execute callback immediately
    // Command handler updates state within the callback
    val behavior = onPersisted(events)

    // unstashAll if stashBuffer is not empty
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  // Emulate the saveSnapshot method of PersistentActor
  override def persistSnapshot(snapshot: S, force: Boolean)(
    onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting snapshot: {}", snapshot)

    // Determine whether to save based on force parameter or snapshot strategy
    val shouldSaveSnapshot = force || config.snapshotCriteria.exists { criteria =>
      // Evaluation for snapshot (using dummy event because there is no event)
      val dummyEvent = snapshot.asInstanceOf[E] // Dummy event (no problem at runtime due to type erasure)
      val sequenceNumber = getCurrentSequenceNumber
      val result = criteria.shouldTakeSnapshot(dummyEvent, snapshot, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSaveSnapshot) {
      // Save snapshot to memory
      InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

      // Update state (directly update in case of snapshot)
      // This is correct behavior because snapshot represents complete state
      currentState = snapshot

      // Apply retention policy (if set)
      config.retentionCriteria.foreach { retention =>
        ctx.log.debug("Applying retention policy: {}", retention)
        // Calculate sequence number to delete based on current sequence number
        val currentSeqNr = getCurrentSequenceNumber
        val maxSeqNrToDelete = calculateMaxSequenceNumberToDelete(currentSeqNr, retention)

        // Actual deletion process (just logging here)
        if (maxSeqNrToDelete > 0) {
          ctx.log.debug("Would delete snapshots up to sequence number: {}", maxSeqNrToDelete)
          // Since the actual InMemoryEventStore does not have a method to delete old snapshots,
          // only log output is performed here as a simulation

        }
      }

      // Execute callback immediately
      val behavior = onPersisted(snapshot)

      // unstashAll if stashBuffer is not empty
      if (!stashBuffer.isEmpty) {
        stashBuffer.unstashAll(behavior)
      } else {
        behavior
      }
    } else {
      ctx.log.debug("Skipping snapshot persistence based on criteria evaluation")
      onPersisted(snapshot)
    }
  }

  override def persistEventWithSnapshot(event: E, snapshot: S, forceSnapshot: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting event with state: {}", event)

    // Save event to memory
    InMemoryEventStore.addEvent(persistenceId, event)

    val sequenceNumber = getCurrentSequenceNumber

    // Save snapshot when evaluating snapshot strategy or force=true
    val shouldSaveSnapshot = forceSnapshot || config.snapshotCriteria.exists { criteria =>
      val result = criteria.shouldTakeSnapshot(event, snapshot, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSaveSnapshot) {
      ctx.log.debug("Taking snapshot at sequence number {}", sequenceNumber)

      // Save snapshot to memory
      InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

      // Update state
      currentState = snapshot

      // Apply retention policy (if set)
      config.retentionCriteria.foreach { retention =>
        ctx.log.debug("Applying retention policy: {}", retention)
        // Calculate sequence number to delete based on current sequence number
        val currentSeqNr = getCurrentSequenceNumber
        val maxSeqNrToDelete = calculateMaxSequenceNumberToDelete(currentSeqNr, retention)

        // Actual deletion process (just logging here)
        if (maxSeqNrToDelete > 0) {
          ctx.log.debug("Would delete snapshots up to sequence number: {}", maxSeqNrToDelete)
        }
      }
    }

    // Execute callback immediately
    val behavior = onPersisted(event)

    // unstashAll if stashBuffer is not empty
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  override def persistEventsWithSnapshot(events: Seq[E], snapshot: S, forceSnapshot: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting events with state: {}", events)

    // Save events to memory
    InMemoryEventStore.addEvents(persistenceId, events)

    val finalSequenceNumber = getCurrentSequenceNumber

    // Save snapshot when evaluating snapshot strategy or force=true
    val shouldSave =
      forceSnapshot || (events.nonEmpty && config.snapshotCriteria.exists { criteria =>
        val lastEvent = events.last
        val result = criteria.shouldTakeSnapshot(lastEvent, snapshot, finalSequenceNumber)
        ctx.log.debug("Snapshot criteria evaluation result: {}", result)
        result
      })

    if (shouldSave) {
      ctx.log.debug("Taking snapshot at sequence number {}", finalSequenceNumber)

      // Save snapshot to memory
      InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

      // Update state
      currentState = snapshot

      // Apply retention policy (if set)
      config.retentionCriteria.foreach { retention =>
        ctx.log.debug("Applying retention policy: {}", retention)
        // Calculate sequence number to delete based on current sequence number
        val currentSeqNr = getCurrentSequenceNumber
        val maxSeqNrToDelete = calculateMaxSequenceNumberToDelete(currentSeqNr, retention)

        // Actual deletion process (just logging here)
        if (maxSeqNrToDelete > 0) {
          ctx.log.debug("Would delete snapshots up to sequence number: {}", maxSeqNrToDelete)
        }
      }
    }

    // Execute callback immediately
    val behavior = onPersisted(events)

    // unstashAll if stashBuffer is not empty
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  /**
   * Get the current state of the entity.
   * This method is primarily used for testing and debugging.
   *
   * @return Current state of the entity
   */
  def getState: S = currentState
}

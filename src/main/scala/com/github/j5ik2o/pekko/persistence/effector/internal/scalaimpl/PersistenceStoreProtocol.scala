package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import org.apache.pekko.actor.typed.ActorRef

/**
 * Protocol for persistence store operations. This object defines the messages used for
 * communication between the persistence effector and the persistence store.
 */
private[effector] object PersistenceStoreProtocol {

  /**
   * Base trait for all persistence commands.
   *
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  sealed trait PersistenceCommand[S, E]

  /**
   * Command to persist a single event.
   *
   * @param event
   *   Event to persist
   * @param replyTo
   *   Actor reference to send the reply to
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class PersistSingleEvent[S, E](
    event: E,
    replyTo: ActorRef[PersistSingleEventSucceeded[S, E]])
    extends PersistenceCommand[S, E]

  /**
   * Command to persist multiple events.
   *
   * @param events
   *   Events to persist
   * @param replyTo
   *   Actor reference to send the reply to
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class PersistMultipleEvents[S, E](
    events: Seq[E],
    replyTo: ActorRef[PersistMultipleEventsSucceeded[S, E]],
  ) extends PersistenceCommand[S, E]

  /**
   * Command to persist a snapshot.
   *
   * @param snapshot
   *   Snapshot to persist
   * @param replyTo
   *   Actor reference to send the reply to
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class PersistSnapshot[S, E](snapshot: S, replyTo: ActorRef[PersistSnapshotReply[S, E]])
    extends PersistenceCommand[S, E]

  /**
   * Command to delete snapshots.
   *
   * @param maxSequenceNumber
   *   Maximum sequence number of snapshots to delete
   * @param replyTo
   *   Actor reference to send the reply to
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class DeleteSnapshots[S, E](
    maxSequenceNumber: Long,
    replyTo: ActorRef[PersistenceReply[S, E]],
  ) extends PersistenceCommand[S, E]

  /**
   * Base trait for all persistence replies.
   *
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  sealed trait PersistenceReply[S, E]

  /**
   * Reply for successful persistence of a single event.
   *
   * @param event
   *   Persisted event
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class PersistSingleEventSucceeded[S, E](event: E) extends PersistenceReply[S, E]

  /**
   * Reply for successful persistence of multiple events.
   *
   * @param events
   *   Persisted events
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class PersistMultipleEventsSucceeded[S, E](events: Seq[E])
    extends PersistenceReply[S, E]

  /**
   * Base trait for snapshot persistence replies.
   *
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  sealed trait PersistSnapshotReply[S, E] extends PersistenceReply[S, E]

  /**
   * Reply for successful persistence of a snapshot.
   *
   * @param snapshot
   *   Persisted snapshot
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class PersistSnapshotSucceeded[S, E](snapshot: S) extends PersistSnapshotReply[S, E]

  /**
   * Reply for failed persistence of a snapshot.
   *
   * @param snapshot
   *   Snapshot that failed to persist
   * @param cause
   *   Cause of the failure
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class PersistSnapshotFailed[S, E](snapshot: S, cause: Throwable)
    extends PersistSnapshotReply[S, E]

  /**
   * Base trait for snapshot deletion replies.
   *
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  sealed trait DeleteSnapshotsReply[S, E] extends PersistenceReply[S, E]

  /**
   * Reply for successful deletion of snapshots.
   *
   * @param maxSequenceNumber
   *   Maximum sequence number of deleted snapshots
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class DeleteSnapshotsSucceeded[S, E](maxSequenceNumber: Long)
    extends DeleteSnapshotsReply[S, E]

  /**
   * Reply for failed deletion of snapshots.
   *
   * @param maxSequenceNumber
   *   Maximum sequence number of snapshots that failed to delete
   * @param cause
   *   Cause of the failure
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   */
  final case class DeleteSnapshotsFailed[S, E](maxSequenceNumber: Long, cause: Throwable)
    extends DeleteSnapshotsReply[S, E]

  /**
   * Message indicating that recovery is complete.
   *
   * @param state
   *   Recovered state
   * @param sequenceNr
   *   Sequence number of the last recovered event
   * @tparam S
   *   Type of state
   */
  final case class RecoveryDone[S](state: S, sequenceNr: Long)
}

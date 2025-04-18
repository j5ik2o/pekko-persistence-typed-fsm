package com.github.j5ik2o.pekko.persistence.effector.internal

import org.apache.pekko.actor.typed.ActorRef

private[effector] object PersistenceStoreProtocol {

  sealed trait PersistenceCommand[S, E]

  final case class PersistSingleEvent[S, E](
    event: E,
    replyTo: ActorRef[PersistSingleEventSucceeded[S, E]])
    extends PersistenceCommand[S, E]

  final case class PersistMultipleEvents[S, E](
    events: Seq[E],
    replyTo: ActorRef[PersistMultipleEventsSucceeded[S, E]],
  ) extends PersistenceCommand[S, E]

  final case class PersistSnapshot[S, E](snapshot: S, replyTo: ActorRef[PersistSnapshotReply[S, E]])
    extends PersistenceCommand[S, E]

  final case class DeleteSnapshots[S, E](
    maxSequenceNumber: Long,
    replyTo: ActorRef[PersistenceReply[S, E]],
  ) extends PersistenceCommand[S, E]

  sealed trait PersistenceReply[S, E]

  final case class PersistSingleEventSucceeded[S, E](event: E) extends PersistenceReply[S, E]

  final case class PersistMultipleEventsSucceeded[S, E](events: Seq[E])
    extends PersistenceReply[S, E]

  sealed trait PersistSnapshotReply[S, E] extends PersistenceReply[S, E]

  final case class PersistSnapshotSucceeded[S, E](snapshot: S) extends PersistSnapshotReply[S, E]

  final case class PersistSnapshotFailed[S, E](snapshot: S, cause: Throwable)
    extends PersistSnapshotReply[S, E]

  sealed trait DeleteSnapshotsReply[S, E] extends PersistenceReply[S, E]

  final case class DeleteSnapshotsSucceeded[S, E](maxSequenceNumber: Long)
    extends DeleteSnapshotsReply[S, E]

  final case class DeleteSnapshotsFailed[S, E](maxSequenceNumber: Long, cause: Throwable)
    extends DeleteSnapshotsReply[S, E]

  final case class RecoveryDone[S](state: S, sequenceNr: Long) // シーケンス番号を追加
}

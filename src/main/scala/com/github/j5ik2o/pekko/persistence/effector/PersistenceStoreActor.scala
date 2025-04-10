package com.github.j5ik2o.pekko.persistence.effector

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.{ActorLogging, Props, Stash}
import org.apache.pekko.persistence.{
  PersistentActor,
  RecoveryCompleted,
  SaveSnapshotFailure,
  SaveSnapshotSuccess,
  SnapshotOffer,
  SnapshotSelectionCriteria,
}

import scala.compiletime.asMatchable

object PersistenceStoreActor {
  trait PersistenceCommand[S, E]
  trait PersistenceReply[S, E]

  final case class PersistSingleEvent[S, E](
    event: E,
    replyTo: ActorRef[PersistSingleEventSucceeded[S, E]])
    extends PersistenceCommand[S, E]
  final case class PersistSingleEventSucceeded[S, E](event: E) extends PersistenceReply[S, E]

  final case class PersistMultipleEvents[S, E](
    events: Seq[E],
    replyTo: ActorRef[PersistMultipleEventsSucceeded[S, E]],
  ) extends PersistenceCommand[S, E]
  final case class PersistMultipleEventsSucceeded[S, E](events: Seq[E])
    extends PersistenceReply[S, E]

  final case class PersistSnapshot[S, E](snapshot: S, replyTo: ActorRef[PersistSnapshotReply[S, E]])
    extends PersistenceCommand[S, E]
  sealed trait PersistSnapshotReply[S, E] extends PersistenceReply[S, E]
  final case class PersistSnapshotSucceeded[S, E](snapshot: S) extends PersistSnapshotReply[S, E]
  final case class PersistSnapshotFailed[S, E](snapshot: S, cause: Throwable)
    extends PersistSnapshotReply[S, E]

  final case class DeleteSnapshots[S, E](
    maxSequenceNumber: Long,
    replyTo: ActorRef[PersistenceReply[S, E]],
  ) extends PersistenceCommand[S, E]
  sealed trait DeleteSnapshotsReply[S, E] extends PersistenceReply[S, E]
  final case class DeleteSnapshotsSucceeded[S, E](maxSequenceNumber: Long)
    extends DeleteSnapshotsReply[S, E]
  final case class DeleteSnapshotsFailed[S, E](maxSequenceNumber: Long, cause: Throwable)
    extends DeleteSnapshotsReply[S, E]

  final case class RecoveryDone[S](state: S)

  def props[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    recoveryActorRef: ActorRef[RecoveryDone[S]],
  ): Props = Props(
    new PersistenceStoreActor[S, E, M](persistenceId, initialState, applyEvent, recoveryActorRef))
}

final class PersistenceStoreActor[S, E, M](
  override val persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  recoveryActorRef: ActorRef[PersistenceStoreActor.RecoveryDone[S]])
  extends PersistentActor
  with ActorLogging
  with Stash {

  import PersistenceStoreActor.*

  private var recoveryState: Option[S] = Some(initialState)

  override def receiveRecover: Receive = { msg =>
    msg.asMatchable match {
      case SnapshotOffer(_, snapshot) =>
        log.debug("receiveRecover: SnapshotOffer: {}", snapshot)
        recoveryState = Some(snapshot.asInstanceOf[S])
      case RecoveryCompleted =>
        log.debug("receiveRecover: RecoveryCompleted")
        recoveryActorRef ! RecoveryDone(
          recoveryState.getOrElse(throw new IllegalStateException("State is not set")))
        recoveryState = None
      case event =>
        if (event != null) {
          log.debug("receiveRecover: Event: {}", event)
          val e = event.asInstanceOf[E]
          recoveryState = Some(applyEvent(recoveryState.getOrElse(throw new AssertionError()), e))
        }
    }
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit =
    super.onPersistFailure(cause, event, seqNr)

  private def waitForDeleteSnapshots(
    maxSequenceNumber: Long,
    replyTo: ActorRef[DeleteSnapshotsReply[S, E]]): Receive = { msg =>
    msg.asMatchable match {
      case msg @ DeleteSnapshotsSucceeded(_) =>
        log.debug("DeleteSnapshotsSucceeded: maxSequenceNumber = {}", maxSequenceNumber)
        // DeleteSnapshotsSucceededメッセージで必ずマッピングされたmaxSequenceNumberが返されるように
        replyTo ! DeleteSnapshotsSucceeded(maxSequenceNumber)
        context.unbecome()
        unstashAll()
      case msg @ DeleteSnapshotsFailed(_, cause) =>
        log.error(cause, "DeleteSnapshotsFailed: maxSequenceNumber = {}", maxSequenceNumber)
        replyTo ! DeleteSnapshotsFailed(maxSequenceNumber, cause)
        context.unbecome()
        unstashAll()
      case other =>
        log.debug("Stashing message while waiting for delete snapshot result: {}", other)
        stash()
    }
  }

  private def waitForSaveSnapshot(
    snapshot: S,
    replyTo: ActorRef[PersistSnapshotReply[S, E]],
  ): Receive = { msg =>
    msg.asMatchable match {
      case msg @ SaveSnapshotSuccess(_) =>
        log.debug("SaveSnapshotSuccess")
        replyTo ! PersistSnapshotSucceeded(snapshot)
        context.unbecome()
        unstashAll()
      case msg @ SaveSnapshotFailure(_, cause) =>
        log.error(cause, "SaveSnapshotFailure")
        replyTo ! PersistSnapshotFailed(snapshot, cause)
        context.unbecome()
        unstashAll()
      case other =>
        log.debug("Stashing message while waiting for snapshot result: {}", other)
        stash()
    }
  }

  override def receiveCommand: Receive = { cmd =>
    cmd.asMatchable match {
      case cmd: PersistSingleEvent[?, ?] =>
        log.debug("PersistSingleEvent: {}", cmd)
        val typedCmd = cmd.asInstanceOf[PersistSingleEvent[S, E]]
        val event = typedCmd.event
        val replyTo = typedCmd.replyTo
        persist(event) { evt =>
          replyTo ! PersistSingleEventSucceeded(evt)
        }
      case cmd: PersistMultipleEvents[?, ?] =>
        log.debug("PersistEventSequence: {}", cmd)
        val typedCmd = cmd.asInstanceOf[PersistMultipleEvents[S, E]]
        val events = typedCmd.events
        val replyTo = typedCmd.replyTo
        var counter = 0
        persistAll(events) { evt =>
          counter += 1
          if (counter == events.size) {
            replyTo ! PersistMultipleEventsSucceeded(events)
          }
        }
      case cmd: PersistSnapshot[?, ?] =>
        log.debug("PersistSnapshot: {}", cmd)
        val typedCmd = cmd.asInstanceOf[PersistSnapshot[S, E]]
        val snapshot = typedCmd.snapshot
        val replyTo = typedCmd.replyTo
        saveSnapshot(snapshot)
        context.become(waitForSaveSnapshot(snapshot, replyTo))
      case cmd: DeleteSnapshots[?, ?] =>
        log.debug("DeleteSnapshot: {}", cmd)
        val typedCmd = cmd.asInstanceOf[DeleteSnapshots[S, E]]
        val maxSequenceNumber = typedCmd.maxSequenceNumber
        val replyTo = typedCmd.replyTo
        deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = maxSequenceNumber))
        context.become(waitForDeleteSnapshots(maxSequenceNumber, replyTo))
    }
  }
}

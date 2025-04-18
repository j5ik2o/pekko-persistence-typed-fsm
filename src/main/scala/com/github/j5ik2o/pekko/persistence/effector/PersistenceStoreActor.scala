package com.github.j5ik2o.pekko.persistence.effector

import com.github.j5ik2o.pekko.persistence.effector.PersistenceStoreProtocol.*
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.{ActorLogging, Props, Stash}
import org.apache.pekko.pattern.{BackoffOpts, BackoffSupervisor}
import org.apache.pekko.persistence.*

import scala.compiletime.asMatchable

private[effector] object PersistenceStoreActor {

  def props[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    recoveryActorRef: ActorRef[RecoveryDone[S]],
    backoffConfig: Option[BackoffConfig],
  ): Props = {
    val childProps = Props(
      new PersistenceStoreActor[S, E, M](persistenceId, initialState, applyEvent, recoveryActorRef))
    backoffConfig match {
      case Some(BackoffConfig(minBackoff, maxBackoff, randomFactor)) =>
        BackoffSupervisor.props(
          BackoffOpts.onFailure(
            childProps,
            "child",
            minBackoff,
            maxBackoff,
            randomFactor,
          ),
        )
      case None =>
        childProps
    }
  }

}

private[effector] final class PersistenceStoreActor[S, E, M](
  override val persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  recoveryActorRef: ActorRef[RecoveryDone[S]])
  extends PersistentActor
  with ActorLogging
  with Stash {

  private var recoveryState: Option[S] = Some(initialState)

  override def receiveRecover: Receive = { msg =>
    msg.asMatchable match {
      case SnapshotOffer(_, snapshot) =>
        log.debug("receiveRecover: SnapshotOffer: {}", snapshot)
        recoveryState = Some(snapshot.asInstanceOf[S])
      case RecoveryCompleted =>
        log.debug("receiveRecover: RecoveryCompleted")
        recoveryActorRef ! RecoveryDone(
          recoveryState.getOrElse(throw new IllegalStateException("State is not set")),
          lastSequenceNr, // lastSequenceNr を含める
        )
        recoveryState = None
      case event =>
        if (event != null) {
          log.debug("receiveRecover: Event: {}", event)
          val e = event.asInstanceOf[E]
          recoveryState = Some(applyEvent(recoveryState.getOrElse(throw new AssertionError()), e))
        }
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

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit =
    super.onPersistFailure(cause, event, seqNr)

  private def waitForDeleteSnapshots(
    maxSequenceNumber: Long,
    replyTo: ActorRef[DeleteSnapshotsReply[S, E]]): Receive = { msg =>
    msg.asMatchable match {
      case DeleteSnapshotsSuccess(_) =>
        log.debug("DeleteSnapshotsSuccess: maxSequenceNumber = {}", maxSequenceNumber)
        replyTo ! DeleteSnapshotsSucceeded(maxSequenceNumber)
        context.unbecome()
        unstashAll()
      case DeleteSnapshotsFailure(_, cause) =>
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

}

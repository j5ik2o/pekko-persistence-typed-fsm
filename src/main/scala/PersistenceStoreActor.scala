package com.github.j5ik2o.pekko.persistence.typed.fsm

import PersistenceStoreActor.{
  EventSequencePersisted,
  PersistEventSequence,
  PersistSingleEvent,
  PersistSnapshot,
  RecoveryDone,
  SingleEventPersisted,
  SnapshotPersisted,
}

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.{ActorLogging, Props}
import org.apache.pekko.persistence.{PersistentActor, RecoveryCompleted}

import scala.compiletime.asMatchable

object PersistenceStoreActor {
  trait PersistenceCommand[S, E]
  trait PersistenceReply[S, E]

  final case class PersistSingleEvent[S, E](event: E, replyTo: ActorRef[SingleEventPersisted[S, E]])
    extends PersistenceCommand[S, E]
  final case class SingleEventPersisted[S, E](event: E) extends PersistenceReply[S, E]

  final case class PersistEventSequence[S, E](
    events: Seq[E],
    replyTo: ActorRef[EventSequencePersisted[S, E]],
  ) extends PersistenceCommand[S, E]
  final case class EventSequencePersisted[S, E](events: Seq[E]) extends PersistenceReply[S, E]

  final case class PersistSnapshot[S, E](snapshot: S, replyTo: ActorRef[SnapshotPersisted[S, E]])
    extends PersistenceCommand[S, E]
  final case class SnapshotPersisted[S, E](snapshot: S) extends PersistenceReply[S, E]

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
  recoveryActorRef: ActorRef[RecoveryDone[S]])
  extends PersistentActor
  with ActorLogging {

  private var state: Option[S] = Some(initialState)

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      recoveryActorRef ! RecoveryDone(
        state.getOrElse(throw new IllegalStateException("State is not set")))
      state = None
    case event =>
      if (event != null) {
        val e = event.asInstanceOf[E]
        state = Some(applyEvent(state.getOrElse(throw new AssertionError()), e))
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
          replyTo ! SingleEventPersisted(evt)
        }
      case cmd: PersistEventSequence[?, ?] =>
        log.debug("PersistEventSequence: {}", cmd)
        val typedCmd = cmd.asInstanceOf[PersistEventSequence[S, E]]
        val events = typedCmd.events
        val replyTo = typedCmd.replyTo
        var counter = 0
        persistAll(events) { evt =>
          counter += 1
          if (counter == events.size) {
            replyTo ! EventSequencePersisted(events)
          }
        }
      case cmd: PersistSnapshot[?, ?] =>
        log.debug("PersistSnapshot: {}", cmd)
        val typedCmd = cmd.asInstanceOf[PersistSnapshot[S, E]]
        val snapshot = typedCmd.snapshot
        val replyTo = typedCmd.replyTo
        saveSnapshot(snapshot)
        replyTo ! SnapshotPersisted(snapshot)
    }
  }
}

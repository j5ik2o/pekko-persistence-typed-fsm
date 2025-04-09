package com.github.j5ik2o.pekko.persistence.typed.fsm

import EventStoreActor.{
  EventSequencePersisted,
  PersistEventSequence,
  PersistSingleEvent,
  RecoveryDone,
  SingleEventPersisted,
}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.{ActorLogging, Props}
import org.apache.pekko.persistence.{PersistentActor, RecoveryCompleted}

import scala.compiletime.asMatchable

object EventStoreActor {
  trait EventPersistenceCommand[S, E]
  trait EventPersistenceReply[E]

  final case class PersistSingleEvent[S, E](event: E, replyTo: ActorRef[SingleEventPersisted[E]])
    extends EventPersistenceCommand[S, E]
  final case class SingleEventPersisted[E](event: E) extends EventPersistenceReply[E]

  final case class PersistEventSequence[S, E](
    events: Seq[E],
    replyTo: ActorRef[EventSequencePersisted[E]],
  ) extends EventPersistenceCommand[S, E]
  final case class EventSequencePersisted[E](events: Seq[E]) extends EventPersistenceReply[E]

  final case class RecoveryDone[S](state: S)

  def props[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    recoveryActorRef: ActorRef[RecoveryDone[S]],
  ): Props = Props(
    new EventStoreActor[S, E, M](persistenceId, initialState, applyEvent, recoveryActorRef))
}

final class EventStoreActor[S, E, M](
  val persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  recoveryActorRef: ActorRef[RecoveryDone[S]])
  extends PersistentActor
  with ActorLogging {

  private var state: S = initialState

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      recoveryActorRef ! RecoveryDone(state)
    case event =>
      // Use type test and cast instead of pattern matching
      if (event != null) {
        val e = event.asInstanceOf[E]
        state = applyEvent(state, e)
      }
  }

  override def receiveCommand: Receive = { case cmd =>
    // Use explicit type tests instead of pattern matching
    if (cmd.isInstanceOf[PersistSingleEvent[?, ?]]) {
      log.debug("PersistSingleEvent: {}", cmd)
      val typedCmd = cmd.asInstanceOf[PersistSingleEvent[S, E]]
      val event = typedCmd.event
      val replyTo = typedCmd.replyTo
      persist(event) { evt =>
        replyTo ! SingleEventPersisted(evt)
      }
    } else if (cmd.isInstanceOf[PersistEventSequence[?, ?]]) {
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
    }
  }
}

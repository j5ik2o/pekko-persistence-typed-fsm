package com.github.j5ik2o.eff.sm.splitter

import Effector.*

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.{ActorLogging, Props}
import org.apache.pekko.persistence.{PersistentActor, RecoveryCompleted}

import scala.collection.mutable.ArrayBuffer
import scala.compiletime.asMatchable

object EffectActor {
  def props[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    recoveryActorRef: ActorRef[RecoveryDone[S]],
  ): Props = Props(
    new EffectActor[S, E, M](persistenceId, initialState, applyEvent, recoveryActorRef))
}

class EffectActor[S, E, M](
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
    if (cmd.isInstanceOf[PersistOne[?, ?]]) {
      log.debug("PersistOne: {}", cmd)
      val typedCmd = cmd.asInstanceOf[PersistOne[S, E]]
      val event = typedCmd.event
      val replyTo = typedCmd.replyTo
      persist(event) { evt =>
        replyTo ! PersistOneCompleted(evt)
      }
    } else if (cmd.isInstanceOf[PersistAll[?, ?]]) {
      log.debug("PersistAll: {}", cmd)
      val typedCmd = cmd.asInstanceOf[PersistAll[S, E]]
      val events = typedCmd.events
      val replyTo = typedCmd.replyTo
      val _events = ArrayBuffer.empty[E]
      persistAll(events) { evt =>
        _events += evt
      }
      if (_events.nonEmpty) {
        replyTo ! PersistedAllCompleted(_events.toSeq)
      }
    }
  }
}

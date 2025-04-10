package com.github.j5ik2o.pekko.persistence.typed.fsm

import PersistenceStoreActor.{
  PersistEventSequence,
  PersistSingleEvent,
  PersistSnapshot,
  PersistenceCommand,
  PersistenceReply,
}

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

final class DefaultPersistenceEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
  persistenceRef: ActorRef[PersistenceCommand[S, E]],
  adapter: ActorRef[PersistenceReply[S, E]])
  extends PersistenceEffector[S, E, M] {
  import config.*
  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting event: {}", event)
    persistenceRef ! PersistSingleEvent(event, adapter)
    Behaviors.receiveMessagePartial {
      case msg if unwrapPersistedEvents(msg).isDefined =>
        ctx.log.debug("Persisted event: {}", msg)
        val events = unwrapPersistedEvents(msg).get
        stashBuffer.unstashAll(onPersisted(events.head))
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }

  override def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting events: {}", events)
    persistenceRef ! PersistEventSequence(events, adapter)
    Behaviors.receiveMessagePartial {
      case msg if unwrapPersistedEvents(msg).isDefined =>
        ctx.log.debug("Persisted events: {}", msg)
        val events = unwrapPersistedEvents(msg).get
        stashBuffer.unstashAll(onPersisted(events))
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }

  override def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisted snapshot: {}", snapshot)
    persistenceRef ! PersistSnapshot(snapshot, adapter)
    Behaviors.receiveMessagePartial {
      case msg if unwrapPersistedSnapshot(msg).isDefined =>
        ctx.log.debug("Persisted snapshot: {}", msg)
        val state = unwrapPersistedSnapshot(msg).get
        stashBuffer.unstashAll(onPersisted(state))
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }
}

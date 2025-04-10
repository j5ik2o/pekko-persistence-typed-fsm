package com.github.j5ik2o.pekko.persistence.typed.fsm

import EventStoreActor.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.adapter.*

trait Effector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onSaved: S => Behavior[M]): Behavior[M]
}

object Effector {
  def create[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
  )(onReady: PartialFunction[(S, Effector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] =
    create(
      EffectorConfig(
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = applyEvent,
        messageConverter = messageConverter,
      ))(onReady)

  def create[S, E, M](
    config: EffectorConfig[S, E, M],
  )(onReady: PartialFunction[(S, Effector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] = {
    import config.*
    val persistenceRef =
      spawnEventStoreActor(
        context,
        persistenceId,
        initialState,
        applyEvent,
        context.messageAdapter[RecoveryDone[S]](rd => wrapRecoveredState(rd.state)))

    val adapter = context.messageAdapter[EventPersistenceReply[S, E]] {
      case SingleEventPersisted(event) => wrapPersistedEvents(Seq(event))
      case EventSequencePersisted(events) => wrapPersistedEvents(events)
      case SnapshotPersisted(snapshot) => wrapPersistedSnapshot(snapshot)
    }

    def awaitRecovery(): Behavior[M] =
      Behaviors.withStash(config.stashSize) { stashBuffer =>
        Behaviors.receivePartial {
          case (ctx, msg) if unwrapRecoveredState(msg).isDefined =>
            val state = unwrapRecoveredState(msg).get
            val effector = new Effector[S, E, M] {
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

              override def persistEvents(events: Seq[E])(
                onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
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

              override def persistSnapshot(snapshot: S)(onSaved: S => Behavior[M]): Behavior[M] = {
                ctx.log.debug("Persisted snapshot: {}", snapshot)
                persistenceRef ! PersistSnapshot(snapshot, adapter)
                Behaviors.receiveMessagePartial {
                  case msg if unwrapPersistedSnapshot(msg).isDefined =>
                    ctx.log.debug("Persisted snapshot: {}", msg)
                    val state = unwrapPersistedSnapshot(msg).get
                    stashBuffer.unstashAll(onSaved(state))
                  case other =>
                    ctx.log.debug("Stashing message: {}", other)
                    stashBuffer.stash(other)
                    Behaviors.same
                }
              }
            }
            stashBuffer.unstashAll(onReady(state, effector))
          case (ctx, msg) =>
            ctx.log.debug("Stashing message: {}", msg)
            stashBuffer.stash(msg)
            Behaviors.same
        }
      }

    awaitRecovery()
  }

  private def spawnEventStoreActor[M, E, S](
    context: ActorContext[M],
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    recoveryAdapter: ActorRef[RecoveryDone[S]]) =
    context
      .actorOf(
        EventStoreActor.props(
          persistenceId,
          initialState,
          applyEvent,
          recoveryAdapter,
        ),
        s"effector-$persistenceId")
      .toTyped[EventPersistenceCommand[S, E]]

  //  private def spawnPersistenceBehavior[M, E, S](
//    persistenceId: String,
//    initialState: S,
//    applyEvent: (S, E) => S,
//    context: ActorContext[M],
//    recoveryAdapter: ActorRef[RecoveryDone[S]]) =
//    context.spawn(
//      createPersistenceBehavior(persistenceId, initialState, applyEvent, recoveryAdapter),
//      s"effector-$persistenceId")

//  private def createPersistenceBehavior[M, E, S](
//    persistenceId: String,
//    initialState: S,
//    applyEvent: (S, E) => S,
//    recoveryAdapter: ActorRef[RecoveryDone[S]]): EventSourcedBehavior[PersistMessage[S, E], E, S] =
//    EventSourcedBehavior[PersistMessage[S, E], E, S](
//      persistenceId = PersistenceId.ofUniqueId(persistenceId),
//      emptyState = initialState,
//      commandHandler = (state, cmd) =>
//        cmd match {
//          case PersistOne(event, replyTo) =>
//            Effect
//              .persist(event)
//              .thenReply(replyTo)(newState => PersistOneCompleted(Some(newState), event))
//          case PersistAll(events, replyTo) =>
//            Effect
//              .persist(events)
//              .thenReply(replyTo)(newState => PersistedAllCompleted(Some(newState), events))
//        },
//      eventHandler = applyEvent,
//    ).receiveSignal { case (state, RecoveryCompleted) =>
//      recoveryAdapter ! RecoveryDone(state)
//    }
}

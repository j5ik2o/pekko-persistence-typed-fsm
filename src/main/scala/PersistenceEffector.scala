package com.github.j5ik2o.pekko.persistence.typed.fsm

import PersistenceStoreActor.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.adapter.*

trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
}

object PersistenceEffector {
  def create[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] =
    create(
      PersistenceEffectorConfig(
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = applyEvent,
        messageConverter = messageConverter,
      ))(onReady)

  def create[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
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

    val adapter = context.messageAdapter[PersistenceReply[S, E]] {
      case SingleEventPersisted(event) => wrapPersistedEvents(Seq(event))
      case EventSequencePersisted(events) => wrapPersistedEvents(events)
      case SnapshotPersisted(snapshot) => wrapPersistedSnapshot(snapshot)
    }

    def awaitRecovery(): Behavior[M] =
      Behaviors.withStash(config.stashSize) { stashBuffer =>
        Behaviors.receivePartial {
          case (ctx, msg) if unwrapRecoveredState(msg).isDefined =>
            val state = unwrapRecoveredState(msg).get
            val effector = PersistenceEffectorImpl[S, E, M](
              ctx,
              stashBuffer,
              config,
              persistenceRef,
              adapter,
            )
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
        PersistenceStoreActor.props(
          persistenceId,
          initialState,
          applyEvent,
          recoveryAdapter,
        ),
        s"effector-$persistenceId")
      .toTyped[PersistenceCommand[S, E]]

}

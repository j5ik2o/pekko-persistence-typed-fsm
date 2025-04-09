package com.github.j5ik2o.eff.sm.splitter

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.adapter.*
trait Effector[S, E, M] {
  def persist(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistAll(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
}

object Effector {
  private[splitter] trait PersistMessage[S, E]
  private[splitter] trait PersistReply[E]
  private[splitter] final case class PersistOne[S, E](
    event: E,
    replyTo: ActorRef[PersistOneCompleted[E]])
    extends PersistMessage[S, E]
  private[splitter] final case class PersistOneCompleted[E](event: E) extends PersistReply[E]

  private[splitter] final case class PersistAll[S, E](
    events: Seq[E],
    replyTo: ActorRef[PersistedAllCompleted[E]],
  ) extends PersistMessage[S, E]
  private[splitter] final case class PersistedAllCompleted[E](events: Seq[E])
    extends PersistReply[E]

  private[splitter] final case class RecoveryDone[S](state: S)

  def create[S, E, M](
    config: EffectorConfig[S, E, M],
  )(onReady: PartialFunction[(S, Effector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] = {
    import config.*
    val recoveryAdapter: ActorRef[RecoveryDone[S]] =
      context.messageAdapter[RecoveryDone[S]](rd => wrapRecovered(rd.state))

    val persistenceRef =
      spawnPersistenceBehavior(persistenceId, initialState, applyEvent, context, recoveryAdapter)

    val adapter = context.messageAdapter[PersistReply[E]] {
      case PersistOneCompleted(event) => wrapPersisted(Seq(event))
      case PersistedAllCompleted(events) => wrapPersisted(events)
    }

    def awaitRecovery(): Behavior[M] =
      Behaviors.withStash(32) { buffer =>
        Behaviors.receivePartial {
          case (ctx, msg) if unwrapRecovered(msg).isDefined =>
            val state = unwrapRecovered(msg).get
            val effector = new Effector[S, E, M] {
              override def persist(event: E)(onPersisted: (E) => Behavior[M]): Behavior[M] = {
                persistenceRef ! PersistOne(event, adapter)
                Behaviors.receiveMessagePartial {
                  case msg if unwrapPersisted(msg).isDefined =>
                    val events = unwrapPersisted(msg).get
                    buffer.unstashAll(onPersisted(events.head))
                  case other =>
                    buffer.stash(other)
                    Behaviors.same
                }
              }

              override def persistAll(events: Seq[E])(
                onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
                persistenceRef ! PersistAll(events, adapter)
                Behaviors.receiveMessagePartial {
                  case msg if unwrapPersisted(msg).isDefined =>
                    val events = unwrapPersisted(msg).get
                    buffer.unstashAll(onPersisted(events))
                  case other =>
                    buffer.stash(other)
                    Behaviors.same
                }
              }
            }
            buffer.unstashAll(onReady(state, effector))
          case (ctx, msg) =>
            ctx.log.info("Stashing message: {}", msg)
            buffer.stash(msg)
            Behaviors.same
        }
      }

    awaitRecovery()
  }

  private def spawnPersistenceBehavior[M, E, S](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    context: ActorContext[M],
    recoveryAdapter: ActorRef[RecoveryDone[S]]) =
    context
      .actorOf(
        EffectActor.props(
          persistenceId,
          initialState,
          applyEvent,
          recoveryAdapter,
        ),
        s"effector-$persistenceId")
      .toTyped[PersistMessage[S, E]]

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

package com.github.j5ik2o.eff.sm.splitter

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.persistence.typed.{PersistenceId, RecoveryCompleted}
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

trait Effector[S, E, M] {
  def persist(event: E)(onPersisted: (S, E) => Behavior[M]): Behavior[M]
  def persistAll(events: Seq[E])(onPersisted: (S, Seq[E]) => Behavior[M]): Behavior[M]
}

object Effector {
  private trait PersistMessage[S, E]
  private trait PersistReply[S, E] { def newState: S }
  private final case class PersistOne[S, E](event: E, replyTo: ActorRef[PersistOneCompleted[S, E]])
    extends PersistMessage[S, E]
  private final case class PersistOneCompleted[S, E](override val newState: S, event: E)
    extends PersistReply[S, E]

  private final case class PersistAll[S, E](
    events: Seq[E],
    replyTo: ActorRef[PersistedAllCompleted[S, E]],
  ) extends PersistMessage[S, E]
  private final case class PersistedAllCompleted[S, E](override val newState: S, events: Seq[E])
    extends PersistReply[S, E]

  private final case class RecoveryDone[S](state: S)

  def create[S, E, M](
    config: EffectorConfig[S, E, M],
  )(onReady: PartialFunction[(S, Effector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] = {
    import config.*

    val recoveryAdapter = context.messageAdapter[RecoveryDone[S]](rd => wrapRecovered(rd.state))

    val persistenceBehavior = EventSourcedBehavior[PersistMessage[S, E], E, S](
      persistenceId = PersistenceId.ofUniqueId(persistenceId),
      emptyState = initialState,
      commandHandler = (state, cmd) =>
        cmd match {
          case PersistOne(event, replyTo) =>
            Effect
              .persist(event)
              .thenReply(replyTo)(newState => PersistOneCompleted(newState, event))
          case PersistAll(events, replyTo) =>
            Effect
              .persist(events)
              .thenReply(replyTo)(newState => PersistedAllCompleted(newState, events))
        },
      eventHandler = applyEvent,
    ).receiveSignal { case (state, RecoveryCompleted) =>
      recoveryAdapter ! RecoveryDone(state)
    }

    val persistenceRef = context.spawn(persistenceBehavior, s"effector-$persistenceId")
    val adapter = context.messageAdapter[PersistReply[S, E]] {
      case PersistOneCompleted(newState, event) => wrapPersisted(newState, Seq(event))
      case PersistedAllCompleted(newState, events) => wrapPersisted(newState, events)
    }

    def awaitRecovery(): Behavior[M] =
      Behaviors.withStash(32) { buffer =>
        Behaviors.receivePartial {
          case (ctx, msg) if unwrapRecovered(msg).isDefined =>
            val state = unwrapRecovered(msg).get
            val effector = new Effector[S, E, M] {
              override def persist(event: E)(onPersisted: (S, E) => Behavior[M]): Behavior[M] = {
                persistenceRef ! PersistOne(event, adapter)
                Behaviors.receiveMessagePartial {
                  case msg if unwrapPersisted(msg).isDefined =>
                    val (newState, events) = unwrapPersisted(msg).get
                    buffer.unstashAll(onPersisted(newState, events.head))
                  case other =>
                    buffer.stash(other)
                    Behaviors.same
                }
              }

              override def persistAll(events: Seq[E])(
                onPersisted: (S, Seq[E]) => Behavior[M]): Behavior[M] = {
                persistenceRef ! PersistAll(events, adapter)
                Behaviors.receiveMessagePartial {
                  case msg if unwrapPersisted(msg).isDefined =>
                    val (newState, events) = unwrapPersisted(msg).get
                    buffer.unstashAll(onPersisted(newState, events))
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
}

package com.github.j5ik2o.pekko.persistence.effector

import PersistenceStoreActor.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.adapter.*

trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]

  /**
   * スナップショットを永続化する（後方互換性のため）
   *
   * @param snapshot
   *   永続化するスナップショット
   * @param onPersisted
   *   スナップショットが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M] =
    persistSnapshot(snapshot, force = false)(onPersisted)

  /**
   * スナップショットを永続化する
   *
   * @param snapshot
   *   永続化するスナップショット
   * @param force
   *   trueの場合、スナップショット戦略を無視して強制的に保存する
   * @param onPersisted
   *   スナップショットが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistSnapshot(snapshot: S, force: Boolean)(onPersisted: S => Behavior[M]): Behavior[M]

  /**
   * イベントを永続化し、現在の状態を指定してスナップショット戦略を評価する（後方互換性のため）
   *
   * @param event
   *   イベント
   * @param state
   *   現在の状態
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventWithState(event: E, state: S)(onPersisted: E => Behavior[M]): Behavior[M] =
    persistEventWithState(event, state, force = false)(onPersisted)

  /**
   * イベントを永続化し、現在の状態を指定してスナップショット戦略を評価する
   *
   * @param event
   *   イベント
   * @param state
   *   現在の状態
   * @param force
   *   trueの場合、スナップショット戦略を無視して強制的にスナップショットを保存する
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventWithState(event: E, state: S, force: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M]

  /**
   * 複数のイベントを永続化し、現在の状態を指定してスナップショット戦略を評価する（後方互換性のため）
   *
   * @param events
   *   イベントのシーケンス
   * @param state
   *   現在の状態
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventsWithState(events: Seq[E], state: S)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] =
    persistEventsWithState(events, state, force = false)(onPersisted)

  /**
   * 複数のイベントを永続化し、現在の状態を指定してスナップショット戦略を評価する
   *
   * @param events
   *   イベントのシーケンス
   * @param state
   *   現在の状態
   * @param force
   *   trueの場合、スナップショット戦略を無視して強制的にスナップショットを保存する
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventsWithState(events: Seq[E], state: S, force: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M]
}

object PersistenceEffector {
  def create[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = 32,
    snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
    retentionCriteria: Option[RetentionCriteria] = None,
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] =
    create(
      PersistenceEffectorConfig(
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = applyEvent,
        messageConverter = messageConverter,
        persistenceMode = PersistenceMode.Persisted,
        stashSize = stashSize,
        snapshotCriteria = snapshotCriteria,
        retentionCriteria = retentionCriteria,
      ))(onReady)

  def create[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] =
    createWithMode(config, config.persistenceMode)(onReady)

  // インメモリモード用のcreateメソッド
  def createInMemory[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = 32,
    snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
    retentionCriteria: Option[RetentionCriteria] = None,
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] = {
    // 設定を作成
    val config = PersistenceEffectorConfig(
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      messageConverter = messageConverter,
      persistenceMode = PersistenceMode.InMemory,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
    )

    createWithMode(config, PersistenceMode.InMemory)(onReady)
  }

  // モードを指定してEffectorを作成するメソッド
  def createWithMode[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
    mode: PersistenceMode,
  )(onReady: PartialFunction[(S, PersistenceEffector[S, E, M]), Behavior[M]])(using
    context: ActorContext[M],
  ): Behavior[M] =
    mode match {
      case PersistenceMode.Persisted =>
        // 既存の永続化実装
        import config.*
        val persistenceRef =
          spawnEventStoreActor(
            context,
            persistenceId,
            initialState,
            applyEvent,
            context.messageAdapter[RecoveryDone[S]](rd => wrapRecoveredState(rd.state)))

        val adapter = context.messageAdapter[PersistenceReply[S, E]] {
          // イベント保存
          case PersistSingleEventSucceeded(event) => wrapPersistedEvents(Seq(event))
          case PersistMultipleEventsSucceeded(events) => wrapPersistedEvents(events)
          // スナップショット保存
          case PersistSnapshotSucceeded(snapshot) => wrapPersistedSnapshot(snapshot)
          case PersistSnapshotFailed(snapshot, cause) =>
            throw new IllegalStateException("Failed to persist snapshot", cause)
          // スナップショット削除
          case DeleteSnapshotsSucceeded(maxSequenceNumber) => wrapDeleteSnapshots(maxSequenceNumber)
          case DeleteSnapshotsFailed(maxSequenceNumber, cause) =>
            throw new IllegalStateException("Failed to delete snapshots", cause)
        }

        def awaitRecovery(): Behavior[M] =
          Behaviors.withStash(config.stashSize) { stashBuffer =>
            Behaviors.receivePartial {
              case (ctx, msg) if unwrapRecoveredState(msg).isDefined =>
                val state = unwrapRecoveredState(msg).get
                val effector = DefaultPersistenceEffector[S, E, M](
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

      case PersistenceMode.InMemory =>
        // インメモリ実装
        Behaviors.withStash(config.stashSize) { stashBuffer =>
          val effector = new InMemoryEffector[S, E, M](
            context,
            stashBuffer,
            config,
          )
          // インメモリなので初期状態を直接使用
          onReady(effector.getState, effector)
        }
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

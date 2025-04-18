package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.PersistenceStoreProtocol.*
import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.DefaultPersistenceEffector
import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.{
  InMemoryEffector,
  PersistenceStoreActor,
}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.compiletime.asMatchable

trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]

  /**
   * スナップショットを永続化する
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
   * @param snapshot
   *   現在の状態
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventWithSnapshot(event: E, snapshot: S)(onPersisted: E => Behavior[M]): Behavior[M] =
    persistEventWithSnapshot(event, snapshot, forceSnapshot = false)(onPersisted)

  /**
   * イベントを永続化し、現在の状態を指定してスナップショット戦略を評価する
   *
   * @param event
   *   イベント
   * @param snapshot
   *   現在の状態
   * @param forceSnapshot
   *   trueの場合、スナップショット戦略を無視して強制的にスナップショットを保存する
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventWithSnapshot(event: E, snapshot: S, forceSnapshot: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M]

  /**
   * 複数のイベントを永続化し、現在の状態を指定してスナップショット戦略を評価する（後方互換性のため）
   *
   * @param events
   *   イベントのシーケンス
   * @param snapshot
   *   現在の状態
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventsWithSnapshot(events: Seq[E], snapshot: S)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] =
    persistEventsWithSnapshot(events, snapshot, forceSnapshot = false)(onPersisted)

  /**
   * 複数のイベントを永続化し、現在の状態を指定してスナップショット戦略を評価する
   *
   * @param events
   *   イベントのシーケンス
   * @param snapshot
   *   現在の状態
   * @param forceSnapshot
   *   trueの場合、スナップショット戦略を無視して強制的にスナップショットを保存する
   * @param onPersisted
   *   イベントが永続化された後に呼び出されるコールバック
   * @return
   *   新しいBehavior
   */
  def persistEventsWithSnapshot(events: Seq[E], snapshot: S, forceSnapshot: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M]
}

object PersistenceEffector {
  // リカバリー完了を内部的に扱うためのメッセージ
  private case class RecoveryCompletedInternal[S](state: S, sequenceNr: Long)

  def create[S, E, M <: Matchable](
    persistenceId: String,
    initialState: S,
    applyEvent: (S, E) => S,
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
    retentionCriteria: Option[RetentionCriteria] = None,
    backoffConfig: Option[BackoffConfig] = None,
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
        backoffConfig = backoffConfig,
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
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
    retentionCriteria: Option[RetentionCriteria] = None,
    backoffConfig: Option[BackoffConfig] = None,
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
      backoffConfig = backoffConfig,
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
        // recoveryAdapter を修正: RecoveryDone から RecoveryCompletedInternal へ変換
        val recoveryAdapter = context.messageAdapter[RecoveryDone[S]] { rd =>
          // state と sequenceNr の両方をラップする
          // M 型にキャストする必要があるため asInstanceOf を使用
          RecoveryCompletedInternal(rd.state, rd.sequenceNr).asInstanceOf[M]
        }

        val persistenceRef = spawnEventStoreActor(
          context,
          persistenceId,
          initialState,
          applyEvent,
          recoveryAdapter,
          backoffConfig,
        )

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
            // receiveMessagePartial を使用し、型安全性を向上
            Behaviors.receiveMessagePartial { msg =>
              // RecoveryCompletedInternal 型で直接マッチング (@unchecked が必要)
              msg.asMatchable match {
                case msg: RecoveryCompletedInternal[?] =>
                  val state = msg.asInstanceOf[RecoveryCompletedInternal[S]].state
                  val sequenceNr =
                    msg.asInstanceOf[RecoveryCompletedInternal[S]].sequenceNr // sequenceNr を抽出
                  context.log.debug(
                    "Recovery completed. State = {}, SequenceNr = {}",
                    state,
                    sequenceNr,
                  ) // context を直接使用
                  val effector = new DefaultPersistenceEffector[S, E, M](
                    context, // context を直接使用
                    stashBuffer,
                    config,
                    persistenceRef,
                    adapter,
                    sequenceNr, // sequenceNr を DefaultPersistenceEffector に渡す
                  )
                  stashBuffer.unstashAll(onReady(state, effector))
                case msg => // 他のメッセージはスタッシュ
                  context.log.debug("Stashing message during recovery: {}", msg) // context を直接使用
                  stashBuffer.stash(msg)
                  Behaviors.same
              }
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
    recoveryAdapter: ActorRef[RecoveryDone[S]],
    backoffConfig: Option[BackoffConfig]) = {
    import org.apache.pekko.actor.typed.scaladsl.adapter.*
    context
      .actorOf(
        PersistenceStoreActor.props(
          persistenceId,
          initialState,
          applyEvent,
          recoveryAdapter,
          backoffConfig,
        ),
        s"effector-$persistenceId",
      )
      .toTyped
  }
}

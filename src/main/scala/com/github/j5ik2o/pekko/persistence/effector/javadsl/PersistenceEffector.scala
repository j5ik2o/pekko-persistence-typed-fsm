package com.github.j5ik2o.pekko.persistence.effector.javadsl

import org.apache.pekko.actor.typed.Behavior

import java.util
import java.util.function.Function

/**
 * Java API for PersistenceEffector.
 *
 * @param S
 *   State type
 * @param E
 *   Event type
 * @param M
 *   Message type
 */
trait PersistenceEffector[S, E, M] {

  /**
   * Persist a single event.
   *
   * @param event
   *   event to persist
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEvent(event: E, onPersisted: Function[E, Behavior[M]]): Behavior[M]

  /**
   * Persist multiple events.
   *
   * @param events
   *   events to persist
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEvents(
    events: util.List[E],
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M]

  /**
   * Persist a snapshot.
   *
   * @param snapshot
   *   snapshot to persist
   * @param onPersisted
   *   callback to be called after the snapshot is persisted
   * @return
   *   new behavior
   */
  def persistSnapshot(snapshot: S, onPersisted: Function[S, Behavior[M]]): Behavior[M] =
    persistSnapshot(snapshot, false, onPersisted)

  /**
   * Persist a snapshot with force option.
   *
   * @param snapshot
   *   snapshot to persist
   * @param force
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after the snapshot is persisted
   * @return
   *   new behavior
   */
  def persistSnapshot(
    snapshot: S,
    force: Boolean,
    onPersisted: Function[S, Behavior[M]]): Behavior[M]

  /**
   * Persist event with snapshot.
   *
   * @param event
   *   event to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEventWithSnapshot(
    event: E,
    snapshot: S,
    onPersisted: Function[E, Behavior[M]]): Behavior[M] =
    persistEventWithSnapshot(event, snapshot, false, onPersisted)

  /**
   * Persist event with snapshot and force option.
   *
   * @param event
   *   event to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param forceSnapshot
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEventWithSnapshot(
    event: E,
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: Function[E, Behavior[M]]): Behavior[M]

  /**
   * Persist multiple events with snapshot.
   *
   * @param events
   *   events to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEventsWithSnapshot(
    events: util.List[E],
    snapshot: S,
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M] =
    persistEventsWithSnapshot(events, snapshot, false, onPersisted)

  /**
   * Persist multiple events with snapshot and force option.
   *
   * @param events
   *   events to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param forceSnapshot
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEventsWithSnapshot(
    events: util.List[E],
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M]
}

object PersistenceEffector {
  import com.github.j5ik2o.pekko.persistence.effector.internal.javaimpl.PersistenceEffectorWrapper
  import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceEffector as ScalaPersistenceEffector
  import org.apache.pekko.actor.typed.Behavior
  import org.apache.pekko.actor.typed.scaladsl.Behaviors
  import java.util.{function, Optional}
  import java.util.function.BiFunction

  /**
   * 永続化モードでPersistenceEffectorを作成する
   *
   * @param persistenceId
   *   永続化ID
   * @param initialState
   *   初期状態
   * @param applyEvent
   *   イベント適用関数
   * @param messageConverter
   *   メッセージコンバーター
   * @param stashSize
   *   スタッシュサイズ
   * @param snapshotCriteria
   *   スナップショット基準
   * @param retentionCriteria
   *   保持基準
   * @param backoffConfig
   *   バックオフ設定
   * @param onReady
   *   準備完了時のコールバック
   * @return
   *   Behavior
   */
  def persisted[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: BiFunction[S, E, S],
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Optional[SnapshotCriteria[S, E]] = Optional.empty(),
    retentionCriteria: Optional[RetentionCriteria] = Optional.empty(),
    backoffConfig: Optional[BackoffConfig] = Optional.empty(),
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] = {
    val config = new PersistenceEffectorConfig[S, E, M](
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      messageConverter = messageConverter,
      persistenceMode = PersistenceMode.PERSISTENCE,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
      backoffConfig = backoffConfig,
    )
    build(config, onReady)
  }

  /**
   * インメモリモードでPersistenceEffectorを作成する
   *
   * @param persistenceId
   *   永続化ID
   * @param initialState
   *   初期状態
   * @param applyEvent
   *   イベント適用関数
   * @param messageConverter
   *   メッセージコンバーター
   * @param stashSize
   *   スタッシュサイズ
   * @param snapshotCriteria
   *   スナップショット基準
   * @param retentionCriteria
   *   保持基準
   * @param backoffConfig
   *   バックオフ設定
   * @param onReady
   *   準備完了時のコールバック
   * @return
   *   Behavior
   */
  def ephemeral[S, E, M](
    persistenceId: String,
    initialState: S,
    applyEvent: BiFunction[S, E, S],
    messageConverter: MessageConverter[S, E, M],
    stashSize: Int = Int.MaxValue,
    snapshotCriteria: Optional[SnapshotCriteria[S, E]] = Optional.empty(),
    retentionCriteria: Optional[RetentionCriteria] = Optional.empty(),
    backoffConfig: Optional[BackoffConfig] = Optional.empty(),
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] = {
    val config = new PersistenceEffectorConfig[S, E, M](
      persistenceId = persistenceId,
      initialState = initialState,
      applyEvent = applyEvent,
      messageConverter = messageConverter,
      persistenceMode = PersistenceMode.EPHEMERAL,
      stashSize = stashSize,
      snapshotCriteria = snapshotCriteria,
      retentionCriteria = retentionCriteria,
      backoffConfig = backoffConfig,
    )
    build(config, onReady)
  }

  /**
   * 設定を指定してPersistenceEffectorを作成する
   *
   * @param config
   *   設定
   * @param onReady
   *   準備完了時のコールバック
   * @return
   *   Behavior
   */
  def fromConfig[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] =
    build(config, onReady)

  /**
   * 設定に基づいてPersistenceEffectorを構築する
   *
   * @param config
   *   設定
   * @param onReady
   *   準備完了時のコールバック
   * @return
   *   Behavior
   */
  private def build[S, E, M](
    config: PersistenceEffectorConfig[S, E, M],
    onReady: BiFunction[S, PersistenceEffector[S, E, M], Behavior[M]],
  ): Behavior[M] =
    Behaviors.setup { ctx =>
      // ScalaDSL版のPersistenceEffectorを呼び出す
      ScalaPersistenceEffector.fromConfig(config.toScala) { case (state, scalaEffector) =>
        // JavaDSL用のPersistenceEffectorWrapperでラップ
        val javaEffector = PersistenceEffectorWrapper(scalaEffector)
        // onReadyコールバックを呼び出し
        onReady.apply(state, javaEffector)
      }(using ctx)
    }

}

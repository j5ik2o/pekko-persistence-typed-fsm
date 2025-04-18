package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  RetentionCriteria,
}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, StashBuffer}

/**
 * インメモリ版のPersistenceEffector実装
 */
private[effector] final class InMemoryEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
) extends PersistenceEffector[S, E, M] {
  import config.*

  // 初期状態の復元（スナップショット＋イベント）- PersistentActorのreceiveRecoverと同様の役割
  private val latestSnapshot = InMemoryEventStore.getLatestSnapshot[S](persistenceId)
  private var currentState: S = latestSnapshot match {
    case Some(snapshot) =>
      ctx.log.debug(s"Recovered from snapshot for $persistenceId")
      // スナップショットを元に状態を復元し、その後のイベントを適用
      InMemoryEventStore.replayEvents(persistenceId, snapshot, applyEvent)
    case None =>
      ctx.log.debug(s"Starting from initial state for $persistenceId")
      // 初期状態からイベントを適用
      InMemoryEventStore.replayEvents(persistenceId, initialState, applyEvent)
  }

  // 現在のシーケンス番号を取得
  private def getCurrentSequenceNumber: Long =
    InMemoryEventStore.getCurrentSequenceNumber(persistenceId)

  /**
   * RetentionCriteriaに基づいて、削除すべきスナップショットの最大シーケンス番号を計算する
   *
   * @param currentSequenceNumber
   *   現在のシーケンス番号
   * @param retention
   *   保持ポリシー
   * @return
   *   削除すべきスナップショットの最大シーケンス番号（0の場合は削除するスナップショットがない）
   */
  private def calculateMaxSequenceNumberToDelete(
    currentSequenceNumber: Long,
    retention: RetentionCriteria,
  ): Long =
    // snapshotEveryとkeepNSnapshotsの両方が設定されている場合のみ計算
    (retention.snapshotEvery, retention.keepNSnapshots) match {
      case (Some(snapshotEvery), Some(keepNSnapshots)) =>
        // 最新のスナップショットのシーケンス番号を計算
        val latestSnapshotSeqNr = currentSequenceNumber - (currentSequenceNumber % snapshotEvery)

        if (latestSnapshotSeqNr < snapshotEvery) {
          // 最初のスナップショットすら作成されていない場合
          0L
        } else {
          // 保持するスナップショットの最も古いシーケンス番号
          val oldestKeptSnapshot =
            latestSnapshotSeqNr - (snapshotEvery.toLong * (keepNSnapshots - 1))

          if (oldestKeptSnapshot <= 0) {
            // 保持するスナップショットがすべて存在しない場合
            0L
          } else {
            // 削除対象となる最大シーケンス番号（oldestKeptSnapshotの直前のスナップショット）
            val maxSequenceNumberToDelete = oldestKeptSnapshot - snapshotEvery

            if (maxSequenceNumberToDelete <= 0) 0L else maxSequenceNumberToDelete
          }
        }
      case _ =>
        // どちらかの設定が欠けている場合は削除しない
        0L
    }

  // PersistentActorのpersistメソッドをエミュレート
  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting event: {}", event)

    // イベントをメモリに保存
    // 注: PersistentActorのpersistメソッドと同様に、イベントを保存するだけで
    // この時点で状態は更新しない
    InMemoryEventStore.addEvent(persistenceId, event)

    // コールバックを即時実行（永続化待ちがない）
    // コールバック内でコマンドハンドラが状態を更新する
    val behavior = onPersisted(event)

    // stashBufferが空ではない場合はunstashAll
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  // PersistentActorのpersistAllメソッドをエミュレート
  override def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting events: {}", events)

    // イベントをメモリに保存
    // 注: PersistentActorのpersistAllメソッドと同様に、イベントを保存するだけで
    // この時点で状態は更新しない
    InMemoryEventStore.addEvents(persistenceId, events)

    // コールバックを即時実行
    // コールバック内でコマンドハンドラが状態を更新する
    val behavior = onPersisted(events)

    // stashBufferが空ではない場合はunstashAll
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  // PersistentActorのsaveSnapshotメソッドをエミュレート
  override def persistSnapshot(snapshot: S, force: Boolean)(
    onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting snapshot: {}", snapshot)

    // forceパラメータまたはスナップショット戦略に基づいて保存するかどうかを判断
    val shouldSaveSnapshot = force || config.snapshotCriteria.exists { criteria =>
      // スナップショットに対する評価（イベントがないため、ダミーのイベントを使用）
      val dummyEvent = snapshot.asInstanceOf[E] // ダミーのイベント（型消去されるため、実行時には問題ない）
      val sequenceNumber = getCurrentSequenceNumber
      val result = criteria.shouldTakeSnapshot(dummyEvent, snapshot, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSaveSnapshot) {
      // スナップショットをメモリに保存
      InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

      // 状態を更新（スナップショットの場合は直接更新する）
      // スナップショットは完全な状態を表すので、これは正しい動作
      currentState = snapshot

      // スナップショット保存成功メッセージをユーザーアクターに送信
      ctx.self ! messageConverter.wrapPersistedSnapshot(snapshot)

      // 保持ポリシーの適用（設定されている場合）
      config.retentionCriteria.foreach { retention =>
        ctx.log.debug("Applying retention policy: {}", retention)
        // 現在のシーケンス番号に基づいて削除すべきシーケンス番号を計算
        val currentSeqNr = getCurrentSequenceNumber
        val maxSeqNrToDelete = calculateMaxSequenceNumberToDelete(currentSeqNr, retention)

        // 実際の削除処理（ここではログに記録するだけ）
        if (maxSeqNrToDelete > 0) {
          ctx.log.debug("Would delete snapshots up to sequence number: {}", maxSeqNrToDelete)
          // 実際のInMemoryEventStoreには古いスナップショットを削除するメソッドがないため、
          // ここではシミュレーションとしてログ出力のみ行う

          // 削除成功メッセージをユーザーアクターに送信
          ctx.self ! messageConverter.wrapDeleteSnapshots(maxSeqNrToDelete)
        }
      }

      // コールバックを即時実行
      val behavior = onPersisted(snapshot)

      // stashBufferが空ではない場合はunstashAll
      if (!stashBuffer.isEmpty) {
        stashBuffer.unstashAll(behavior)
      } else {
        behavior
      }
    } else {
      ctx.log.debug("Skipping snapshot persistence based on criteria evaluation")
      onPersisted(snapshot)
    }
  }

  override def persistEventWithSnapshot(event: E, snapshot: S, forceSnapshot: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting event with state: {}", event)

    // イベントをメモリに保存
    InMemoryEventStore.addEvent(persistenceId, event)

    val sequenceNumber = getCurrentSequenceNumber

    // スナップショット戦略の評価またはforce=trueの場合にスナップショットを保存
    val shouldSaveSnapshot = forceSnapshot || config.snapshotCriteria.exists { criteria =>
      val result = criteria.shouldTakeSnapshot(event, snapshot, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSaveSnapshot) {
      ctx.log.debug("Taking snapshot at sequence number {}", sequenceNumber)

      // スナップショットをメモリに保存
      InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

      // 状態を更新
      currentState = snapshot

      // スナップショット保存成功メッセージをユーザーアクターに送信
      ctx.self ! messageConverter.wrapPersistedSnapshot(snapshot)

      // 保持ポリシーの適用（設定されている場合）
      config.retentionCriteria.foreach { retention =>
        ctx.log.debug("Applying retention policy: {}", retention)
        // 現在のシーケンス番号に基づいて削除すべきシーケンス番号を計算
        val currentSeqNr = getCurrentSequenceNumber
        val maxSeqNrToDelete = calculateMaxSequenceNumberToDelete(currentSeqNr, retention)

        // 実際の削除処理（ここではログに記録するだけ）
        if (maxSeqNrToDelete > 0) {
          ctx.log.debug("Would delete snapshots up to sequence number: {}", maxSeqNrToDelete)
          // 削除成功メッセージをユーザーアクターに送信
          ctx.self ! messageConverter.wrapDeleteSnapshots(maxSeqNrToDelete)
        }
      }
    }

    // コールバックを即時実行
    val behavior = onPersisted(event)

    // stashBufferが空ではない場合はunstashAll
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  override def persistEventsWithSnapshot(events: Seq[E], snapshot: S, forceSnapshot: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting events with state: {}", events)

    // イベントをメモリに保存
    InMemoryEventStore.addEvents(persistenceId, events)

    val finalSequenceNumber = getCurrentSequenceNumber

    // スナップショット戦略の評価またはforce=trueの場合にスナップショットを保存
    val shouldSave =
      forceSnapshot || (events.nonEmpty && config.snapshotCriteria.exists { criteria =>
        val lastEvent = events.last
        val result = criteria.shouldTakeSnapshot(lastEvent, snapshot, finalSequenceNumber)
        ctx.log.debug("Snapshot criteria evaluation result: {}", result)
        result
      })

    if (shouldSave) {
      ctx.log.debug("Taking snapshot at sequence number {}", finalSequenceNumber)

      // スナップショットをメモリに保存
      InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

      // 状態を更新
      currentState = snapshot

      // スナップショット保存成功メッセージをユーザーアクターに送信
      ctx.self ! messageConverter.wrapPersistedSnapshot(snapshot)

      // 保持ポリシーの適用（設定されている場合）
      config.retentionCriteria.foreach { retention =>
        ctx.log.debug("Applying retention policy: {}", retention)
        // 現在のシーケンス番号に基づいて削除すべきシーケンス番号を計算
        val currentSeqNr = getCurrentSequenceNumber
        val maxSeqNrToDelete = calculateMaxSequenceNumberToDelete(currentSeqNr, retention)

        // 実際の削除処理（ここではログに記録するだけ）
        if (maxSeqNrToDelete > 0) {
          ctx.log.debug("Would delete snapshots up to sequence number: {}", maxSeqNrToDelete)
          // 削除成功メッセージをユーザーアクターに送信
          ctx.self ! messageConverter.wrapDeleteSnapshots(maxSeqNrToDelete)
        }
      }
    }

    // コールバックを即時実行
    val behavior = onPersisted(events)

    // stashBufferが空ではない場合はunstashAll
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  // アクセサメソッド - テスト用および状態を取得するため
  def getState: S = currentState
}

package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.PersistenceStoreProtocol.*
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  RetentionCriteria,
}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.compiletime.asMatchable

private[effector] final class DefaultPersistenceEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
  persistenceRef: ActorRef[PersistenceCommand[S, E]],
  adapter: ActorRef[PersistenceReply[S, E]],
  initialSequenceNr: Long,
) extends PersistenceEffector[S, E, M] {
  import config.*

  // 現在のシーケンス番号をPersistenceIdごとに管理
  // 初期値を initialSequenceNr で設定
  private val sequenceNumbers =
    scala.collection.mutable.Map[String, Long](persistenceId -> initialSequenceNr)

  // getOrElse のデフォルト値を initialSequenceNr に変更 (ただし、通常はマップに存在するはず)
  private def getCurrentSequenceNumber: Long =
    sequenceNumbers.getOrElse(persistenceId, initialSequenceNr)

  private def incrementSequenceNumber(inc: Long = 1): Long = {
    val current = getCurrentSequenceNumber
    val newValue = current + inc
    sequenceNumbers.update(persistenceId, newValue)
    newValue
  }

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
        // 計算値をログに出力
        ctx.log.debug(
          "Calculating maxSequenceNumberToDelete: currentSequenceNumber={}, snapshotEvery={}, keepNSnapshots={}",
          currentSequenceNumber,
          snapshotEvery,
          keepNSnapshots,
        )

        // 最新のスナップショットのシーケンス番号を計算
        val latestSnapshotSeqNr = currentSequenceNumber - (currentSequenceNumber % snapshotEvery)
        ctx.log.debug("Calculated latestSnapshotSeqNr: {}", latestSnapshotSeqNr)

        if (latestSnapshotSeqNr < snapshotEvery) {
          // 最初のスナップショットすら作成されていない場合
          ctx.log.debug("latestSnapshotSeqNr < snapshotEvery, returning 0")
          0L
        } else {
          // 保持するスナップショットの最も古いシーケンス番号
          val oldestKeptSnapshot =
            latestSnapshotSeqNr - (snapshotEvery.toLong * (keepNSnapshots - 1))
          ctx.log.debug("Calculated oldestKeptSnapshot: {}", oldestKeptSnapshot)

          if (oldestKeptSnapshot <= 0) {
            // 保持するスナップショットがすべて存在しない場合
            ctx.log.debug("oldestKeptSnapshot <= 0, returning 0")
            0L
          } else {
            // 削除対象となる最大シーケンス番号（oldestKeptSnapshotの直前のスナップショット）
            val maxSequenceNumberToDelete = oldestKeptSnapshot - snapshotEvery
            ctx.log.debug("Calculated maxSequenceNumberToDelete: {}", maxSequenceNumberToDelete)

            if (maxSequenceNumberToDelete <= 0) {
              ctx.log.debug("maxSequenceNumberToDelete <= 0, returning 0")
              0L
            } else {
              ctx.log.debug("Returning maxSequenceNumberToDelete: {}", maxSequenceNumberToDelete)
              maxSequenceNumberToDelete
            }
          }
        }
      case _ =>
        // どちらかの設定が欠けている場合は削除しない
        ctx.log.debug("snapshotEvery or keepNSnapshots is None, returning 0")
        0L
    }

  /**
   * 指定されたメッセージタイプを待機する汎用的なメソッド
   *
   * @param messageMatcher
   *   メッセージを検出する関数
   * @param extractResult
   *   メッセージから結果を抽出する関数
   * @param logMessage
   *   成功時のログメッセージ
   * @param onSuccess
   *   成功時のコールバック
   * @tparam T
   *   抽出される結果の型
   * @return
   *   メッセージ待機の振る舞い
   */
  private def waitForMessage[T](
    messageMatcher: M => Option[T],
    logMessage: String,
    onSuccess: T => Behavior[M],
  ): Behavior[M] =
    Behaviors.receiveMessagePartial { msg =>
      ctx.log.debug("Waiting for message: {}", msg)
      msg.asMatchable match {
        case msg if messageMatcher(msg).isDefined =>
          val result = messageMatcher(msg).get
          ctx.log.debug(s"$logMessage: {}", msg)
          stashBuffer.unstashAll(onSuccess(result))
        case other =>
          ctx.log.debug("Stashing message: {}", other)
          stashBuffer.stash(other)
          Behaviors.same
      }
    }

  /**
   * RetentionCriteriaに基づいて古いスナップショットを削除
   *
   * @param retention
   *   保持ポリシー
   * @param onDeleted
   *   削除完了後の動作
   * @return
   *   待機動作
   */
  private def deleteOldSnapshots(
    retention: RetentionCriteria,
    onDeleted: => Behavior[M]): Behavior[M] = {
    val currentSequenceNumber = getCurrentSequenceNumber
    val maxSequenceNumberToDelete =
      calculateMaxSequenceNumberToDelete(currentSequenceNumber, retention)

    if (maxSequenceNumberToDelete > 0) {
      ctx.log.debug(
        "Deleting snapshots with sequence numbers up to {} based on retention policy",
        maxSequenceNumberToDelete,
      )
      persistenceRef ! DeleteSnapshots(maxSequenceNumberToDelete, adapter)
      waitForMessage(
        unwrapDeleteSnapshots,
        "Delete snapshots succeeded",
        _ => {
          // 削除メッセージをユーザーアクターに送信
          ctx.self ! messageConverter.wrapDeleteSnapshots(maxSequenceNumberToDelete)
          onDeleted
        },
      )
    } else {
      ctx.log.debug("No snapshots to delete based on retention policy")
      onDeleted
    }
  }

  /**
   * スナップショットを取得するかどうかを評価する
   *
   * @param event
   *   イベント
   * @param state
   *   状態
   * @param sequenceNumber
   *   シーケンス番号
   * @param force
   *   強制フラグ
   * @return
   *   スナップショットを取得すべきかどうか
   */
  private def shouldTakeSnapshot(
    event: E,
    state: S,
    sequenceNumber: Long,
    force: Boolean): Boolean =
    force || config.snapshotCriteria.exists { criteria =>
      val result = criteria.shouldTakeSnapshot(event, state, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

  /**
   * スナップショット保存を処理する
   *
   * @param state
   *   保存する状態
   * @param onCompleted
   *   スナップショット処理完了後のコールバック
   * @tparam T
   *   コールバックの戻り値の型
   * @return
   *   待機ビヘイビア
   */
  private def handleSnapshotSave[T](state: S, onCompleted: => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Taking snapshot for state: {}", state)
    persistenceRef ! PersistSnapshot(state, adapter)

    waitForMessage(
      unwrapPersistedSnapshot,
      "Persisted snapshot",
      snapshot => {
        // スナップショット保存成功メッセージをユーザーアクターに送信
        ctx.self ! messageConverter.wrapPersistedSnapshot(state)

        // RetentionCriteriaが設定されている場合は古いスナップショットを削除
        config.retentionCriteria match {
          case Some(retention) => deleteOldSnapshots(retention, onCompleted)
          case None => onCompleted
        }
      },
    )
  }

  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting event: {}", event)
    persistenceRef ! PersistSingleEvent(event, adapter)
    incrementSequenceNumber()

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted event",
      events => onPersisted(events.head),
    )
  }

  override def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting events: {}", events)
    persistenceRef ! PersistMultipleEvents(events, adapter)
    incrementSequenceNumber(events.size)

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted events",
      persistedEvents => onPersisted(persistedEvents),
    )
  }

  override def persistSnapshot(snapshot: S, force: Boolean)(
    onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting snapshot: {}", snapshot)

    // forceパラメータまたはスナップショット戦略に基づいて保存するかどうかを判断
    val shouldSaveSnapshot = force || config.snapshotCriteria.exists { criteria =>
      // スナップショットに対する評価（すでに状態がスナップショットとして渡されているため、それを直接使用）
      // イベントがない場合でも評価するため、仮想イベントとしてスナップショット自体を使用
      val dummyEvent = snapshot.asInstanceOf[E] // ダミーのイベント（型消去されるため、実行時には問題ない）
      val sequenceNumber = getCurrentSequenceNumber
      val result = criteria.shouldTakeSnapshot(dummyEvent, snapshot, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSaveSnapshot) {
      handleSnapshotSave(snapshot, stashBuffer.unstashAll(onPersisted(snapshot)))
    } else {
      ctx.log.debug("Skipping snapshot persistence based on criteria evaluation")
      onPersisted(snapshot)
    }
  }

  override def persistEventWithSnapshot(event: E, snapshot: S, forceSnapshot: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting event with state: {}", event)
    persistenceRef ! PersistSingleEvent(event, adapter)
    val sequenceNumber = incrementSequenceNumber()

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted event",
      events => {
        val shouldSaveSnapshot = shouldTakeSnapshot(event, snapshot, sequenceNumber, forceSnapshot)

        if (shouldSaveSnapshot) {
          ctx.log.debug("Taking snapshot at sequence number {}", sequenceNumber)
          handleSnapshotSave(snapshot, stashBuffer.unstashAll(onPersisted(events.head)))
        } else {
          stashBuffer.unstashAll(onPersisted(events.head))
        }
      },
    )
  }

  override def persistEventsWithSnapshot(events: Seq[E], snapshot: S, forceSnapshot: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting events with state: {}", events)
    persistenceRef ! PersistMultipleEvents(events, adapter)
    val finalSequenceNumber = incrementSequenceNumber(events.size)

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted events",
      persistedEvents => {
        // スナップショット戦略の評価またはforce=trueの場合に自動スナップショット取得
        // 最後のイベントとシーケンス番号だけで評価
        val shouldSaveSnapshot =
          forceSnapshot || (events.nonEmpty && config.snapshotCriteria.exists { criteria =>
            val lastEvent = events.last
            val result = criteria.shouldTakeSnapshot(lastEvent, snapshot, finalSequenceNumber)
            ctx.log.debug("Snapshot criteria evaluation result: {}", result)
            result
          })

        if (shouldSaveSnapshot) {
          ctx.log.debug("Taking snapshot at sequence number {}", finalSequenceNumber)
          handleSnapshotSave(snapshot, stashBuffer.unstashAll(onPersisted(persistedEvents)))
        } else {
          stashBuffer.unstashAll(onPersisted(persistedEvents))
        }
      },
    )
  }
}

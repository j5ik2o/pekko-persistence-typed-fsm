package com.github.j5ik2o.pekko.persistence.effector

import com.github.j5ik2o.pekko.persistence.effector.PersistenceStoreActor.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import compiletime.asMatchable

final class DefaultPersistenceEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
  persistenceRef: ActorRef[PersistenceCommand[S, E]],
  adapter: ActorRef[PersistenceReply[S, E]])
  extends PersistenceEffector[S, E, M] {
  import config.*

  // 現在のシーケンス番号をPersistenceIdごとに管理
  private val sequenceNumbers = scala.collection.mutable.Map[String, Long]()

  private def getCurrentSequenceNumber: Long = sequenceNumbers.getOrElse(persistenceId, 0L)

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

  /**
   * スナップショット削除メッセージを待機する動作を作成
   *
   * @param onDeleted
   *   削除完了後の動作を定義する関数
   * @return
   *   スナップショット削除メッセージを待機する動作
   */
  private def waitForDeleteSnapshot(onDeleted: => Behavior[M]): Behavior[M] =
    Behaviors.receiveMessagePartial { msg =>
      msg.asMatchable match {
        case m: DeleteSnapshotsSucceeded[?, ?] =>
          ctx.log.debug("Delete snapshots succeeded: {}", m.maxSequenceNumber)
          stashBuffer.unstashAll(onDeleted)
        case m: DeleteSnapshotsFailed[?, ?] =>
          ctx.log.error("Delete snapshots failed: {}", m)
          stashBuffer.unstashAll(onDeleted)
        case other =>
          ctx.log.debug("Stashing message while waiting for delete snapshot result: {}", other)
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
      waitForDeleteSnapshot(onDeleted)
    } else {
      ctx.log.debug("No snapshots to delete based on retention policy")
      onDeleted
    }
  }

  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting event: {}", event)
    persistenceRef ! PersistSingleEvent(event, adapter)
    incrementSequenceNumber()
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
    persistenceRef ! PersistMultipleEvents(events, adapter)
    incrementSequenceNumber(events.size)
    Behaviors.receiveMessagePartial {
      case msg if unwrapPersistedEvents(msg).isDefined =>
        ctx.log.debug("Persisted events: {}", msg)
        val persistedEvents = unwrapPersistedEvents(msg).get
        stashBuffer.unstashAll(onPersisted(persistedEvents))
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }

  override def persistSnapshot(snapshot: S, force: Boolean)(
    onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting snapshot: {}", snapshot)

    // forceパラメータまたはスナップショット戦略に基づいて保存するかどうかを判断
    val shouldSave = force || config.snapshotCriteria.exists { criteria =>
      // スナップショットに対する評価（すでに状態がスナップショットとして渡されているため、それを直接使用）
      // イベントがない場合でも評価するため、仮想イベントとしてスナップショット自体を使用
      val dummyEvent = snapshot.asInstanceOf[E] // ダミーのイベント（型消去されるため、実行時には問題ない）
      val sequenceNumber = getCurrentSequenceNumber
      val result = criteria.shouldTakeSnapshot(dummyEvent, snapshot, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSave) {
      persistenceRef ! PersistSnapshot(snapshot, adapter)

      Behaviors.receiveMessagePartial {
        case msg if unwrapPersistedSnapshot(msg).isDefined =>
          ctx.log.debug("Persisted snapshot: {}", msg)
          val state = unwrapPersistedSnapshot(msg).get

          // スナップショット保存成功後にretentionCriteriaに基づいて古いスナップショットを削除
          config.retentionCriteria match {
            case Some(retention) =>
              // スナップショット削除を実行し、完了後に元のコールバックを実行
              deleteOldSnapshots(retention, stashBuffer.unstashAll(onPersisted(state)))
            case None =>
              // retentionCriteriaが指定されていない場合はそのまま処理を続行
              stashBuffer.unstashAll(onPersisted(state))
          }
        case other =>
          ctx.log.debug("Stashing message: {}", other)
          stashBuffer.stash(other)
          Behaviors.same
      }
    } else {
      ctx.log.debug("Skipping snapshot persistence based on criteria evaluation")
      onPersisted(snapshot)
    }
  }

  override def persistEventWithState(event: E, state: S, force: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting event with state: {}", event)
    persistenceRef ! PersistSingleEvent(event, adapter)

    val sequenceNumber = incrementSequenceNumber()

    Behaviors.receiveMessagePartial {
      case msg if unwrapPersistedEvents(msg).isDefined =>
        ctx.log.debug("Persisted event: {}", msg)
        val events = unwrapPersistedEvents(msg).get

        // スナップショット戦略の評価またはforce=trueの場合に自動スナップショット取得
        val shouldSave = force || config.snapshotCriteria.exists { criteria =>
          val result = criteria.shouldTakeSnapshot(event, state, sequenceNumber)
          ctx.log.debug("Snapshot criteria evaluation result: {}", result)
          result
        }

        if (shouldSave) {
          ctx.log.debug("Taking snapshot at sequence number {}", sequenceNumber)
          persistenceRef ! PersistSnapshot(state, adapter)

          // スナップショット保存応答を待ってから処理を続行
          Behaviors.receiveMessagePartial {
            case msg if unwrapPersistedSnapshot(msg).isDefined =>
              ctx.log.debug("Persisted snapshot during event persistence: {}", msg)

              // RetentionCriteriaが設定されている場合は古いスナップショットを削除
              config.retentionCriteria match {
                case Some(retention) =>
                  deleteOldSnapshots(retention, stashBuffer.unstashAll(onPersisted(events.head)))
                case None =>
                  stashBuffer.unstashAll(onPersisted(events.head))
              }
            case other =>
              ctx.log.debug("Stashing message while waiting for snapshot result: {}", other)
              stashBuffer.stash(other)
              Behaviors.same
          }
        } else {
          stashBuffer.unstashAll(onPersisted(events.head))
        }
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }

  override def persistEventsWithState(events: Seq[E], state: S, force: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting events with state: {}", events)
    persistenceRef ! PersistMultipleEvents(events, adapter)

    val finalSequenceNumber = incrementSequenceNumber(events.size)

    Behaviors.receiveMessagePartial {
      case msg if unwrapPersistedEvents(msg).isDefined =>
        ctx.log.debug("Persisted events: {}", msg)
        val persistedEvents = unwrapPersistedEvents(msg).get

        // スナップショット戦略の評価またはforce=trueの場合に自動スナップショット取得
        // 最後のイベントとシーケンス番号だけで評価
        val shouldSave = force || (events.nonEmpty && config.snapshotCriteria.exists { criteria =>
          val lastEvent = events.last
          val result = criteria.shouldTakeSnapshot(lastEvent, state, finalSequenceNumber)
          ctx.log.debug("Snapshot criteria evaluation result: {}", result)
          result
        })

        if (shouldSave) {
          ctx.log.debug("Taking snapshot at sequence number {}", finalSequenceNumber)
          persistenceRef ! PersistSnapshot(state, adapter)

          // スナップショット保存応答を待ってから処理を続行
          Behaviors.receiveMessagePartial {
            case msg if unwrapPersistedSnapshot(msg).isDefined =>
              ctx.log.debug("Persisted snapshot during events persistence: {}", msg)

              // RetentionCriteriaが設定されている場合は古いスナップショットを削除
              config.retentionCriteria match {
                case Some(retention) =>
                  deleteOldSnapshots(
                    retention,
                    stashBuffer.unstashAll(onPersisted(persistedEvents)))
                case None =>
                  stashBuffer.unstashAll(onPersisted(persistedEvents))
              }
            case other =>
              ctx.log.debug("Stashing message while waiting for snapshot result: {}", other)
              stashBuffer.stash(other)
              Behaviors.same
          }
        } else {
          stashBuffer.unstashAll(onPersisted(persistedEvents))
        }
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }
}

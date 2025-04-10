package com.github.j5ik2o.pekko.persistence.effector

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, StashBuffer}
import org.apache.pekko.actor.typed.Behavior

/**
 * インメモリのイベントとスナップショットを格納するためのシングルトンオブジェクト
 */
private[effector] object InMemoryEventStore {
  import scala.jdk.CollectionConverters.*

  // スレッドセーフなコレクションを使用
  // persistenceId -> イベントリスト
  private val events: scala.collection.mutable.Map[String, Vector[Any]] =
    new java.util.concurrent.ConcurrentHashMap[String, Vector[Any]]().asScala
  // persistenceId -> 最新スナップショット
  private val snapshots: scala.collection.mutable.Map[String, Any] =
    new java.util.concurrent.ConcurrentHashMap[String, Any]().asScala
  // persistenceId -> スナップショット保存時の最新イベントインデックス
  private val snapshotEventIndices: scala.collection.mutable.Map[String, Int] =
    new java.util.concurrent.ConcurrentHashMap[String, Int]().asScala
  // persistenceId -> 現在のシーケンス番号
  private val sequenceNumbers: scala.collection.mutable.Map[String, Long] =
    new java.util.concurrent.ConcurrentHashMap[String, Long]().asScala

  def addEvent[E](id: String, event: E): Unit = {
    events.updateWith(id) {
      case Some(existing) => Some(existing :+ event)
      case None => Some(Vector(event))
    }
    val currentSeq = sequenceNumbers.getOrElse(id, 0L)
    sequenceNumbers.update(id, currentSeq + 1)
  }

  def addEvents[E](id: String, newEvents: Seq[E]): Unit = {
    events.updateWith(id) {
      case Some(existing) => Some(existing ++ newEvents)
      case None => Some(newEvents.toVector)
    }
    val currentSeq = sequenceNumbers.getOrElse(id, 0L)
    sequenceNumbers.update(id, currentSeq + newEvents.size)
  }

  def getCurrentSequenceNumber(id: String): Long =
    sequenceNumbers.getOrElse(id, 0L)

  def saveSnapshot[S](id: String, snapshot: S): Unit = {
    snapshots.update(id, snapshot)
    // スナップショット保存時点でのイベント数を記録
    snapshotEventIndices.update(id, events.getOrElse(id, Vector.empty).size)
  }

  def getEvents[E](id: String): Vector[E] =
    events.getOrElse(id, Vector.empty).asInstanceOf[Vector[E]]

  def getLatestSnapshot[S](id: String): Option[S] =
    snapshots.get(id).map(_.asInstanceOf[S])

  // スナップショット後のイベントのみを取得
  def getEventsAfterSnapshot[E](id: String): Vector[E] = {
    val allEvents = getEvents[E](id)
    if (snapshots.contains(id) && allEvents.nonEmpty) {
      // スナップショットが存在し、イベントも存在する場合は
      // 最後にスナップショットを作成した後のイベントのみを返す
      val snapshotIndex = snapshotEventIndices.getOrElse(id, 0)
      allEvents.drop(snapshotIndex)
    } else {
      allEvents
    }
  }

  def replayEvents[S, E](id: String, state: S, applyEvent: (S, E) => S): S =
    getEventsAfterSnapshot[E](id).foldLeft(state)(applyEvent)

  // テスト用にストアをクリアするメソッド
  def clear(): Unit = {
    events.clear()
    snapshots.clear()
    snapshotEventIndices.clear()
    sequenceNumbers.clear()
  }
}

/**
 * インメモリ版のPersistenceEffector実装
 */
final class InMemoryEffector[S, E, M](
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
    val shouldSave = force || config.snapshotCriteria.exists { criteria =>
      // スナップショットに対する評価（イベントがないため、ダミーのイベントを使用）
      val dummyEvent = snapshot.asInstanceOf[E] // ダミーのイベント（型消去されるため、実行時には問題ない）
      val sequenceNumber = getCurrentSequenceNumber
      val result = criteria.shouldTakeSnapshot(dummyEvent, snapshot, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSave) {
      // 保持ポリシーの適用（設定されている場合）
      config.retentionCriteria.foreach { retention =>
        // TODO: 実際のretentionポリシーの適用（古いスナップショットの削除など）
        ctx.log.debug("Applying retention policy: {}", retention)
      }

      // スナップショットをメモリに保存
      InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

      // 状態を更新（スナップショットの場合は直接更新する）
      // スナップショットは完全な状態を表すので、これは正しい動作
      currentState = snapshot

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

  override def persistEventWithState(event: E, state: S, force: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting event with state: {}", event)

    // イベントをメモリに保存
    InMemoryEventStore.addEvent(persistenceId, event)

    val sequenceNumber = getCurrentSequenceNumber

    // スナップショット戦略の評価またはforce=trueの場合にスナップショットを保存
    val shouldSave = force || config.snapshotCriteria.exists { criteria =>
      val result = criteria.shouldTakeSnapshot(event, state, sequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSave) {
      ctx.log.debug("Taking snapshot at sequence number {}", sequenceNumber)
      InMemoryEventStore.saveSnapshot(persistenceId, state)
      // 状態も更新
      currentState = state
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

  override def persistEventsWithState(events: Seq[E], state: S, force: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting events with state: {}", events)

    // イベントをメモリに保存
    InMemoryEventStore.addEvents(persistenceId, events)

    val finalSequenceNumber = getCurrentSequenceNumber

    // スナップショット戦略の評価またはforce=trueの場合にスナップショットを保存
    val shouldSave = force || (events.nonEmpty && config.snapshotCriteria.exists { criteria =>
      val lastEvent = events.last
      val result = criteria.shouldTakeSnapshot(lastEvent, state, finalSequenceNumber)
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    })

    if (shouldSave) {
      ctx.log.debug("Taking snapshot at sequence number {}", finalSequenceNumber)
      InMemoryEventStore.saveSnapshot(persistenceId, state)
      // 状態も更新
      currentState = state
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

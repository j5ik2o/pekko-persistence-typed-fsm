package com.github.j5ik2o.pekko.persistence.effector

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, StashBuffer}
import org.apache.pekko.actor.typed.Behavior

/**
 * インメモリのイベントとスナップショットを格納するためのシングルトンオブジェクト
 */
private[effector] object InMemoryEventStore {
  // persistenceId -> イベントリスト
  private val events = scala.collection.mutable.Map[String, Vector[Any]]()
  // persistenceId -> 最新スナップショット
  private val snapshots = scala.collection.mutable.Map[String, Any]()
  // persistenceId -> スナップショット保存時の最新イベントインデックス
  private val snapshotEventIndices = scala.collection.mutable.Map[String, Int]()

  def addEvent[E](id: String, event: E): Unit =
    events.updateWith(id) {
      case Some(existing) => Some(existing :+ event)
      case None => Some(Vector(event))
    }

  def addEvents[E](id: String, newEvents: Seq[E]): Unit =
    events.updateWith(id) {
      case Some(existing) => Some(existing ++ newEvents)
      case None => Some(newEvents.toVector)
    }

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
  override def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting snapshot: {}", snapshot)

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
  }

  // アクセサメソッド - テスト用および状態を取得するため
  def getState: S = currentState
}

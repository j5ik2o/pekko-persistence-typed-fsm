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

  def saveSnapshot[S](id: String, snapshot: S): Unit =
    snapshots.update(id, snapshot)

  def getEvents[E](id: String): Vector[E] =
    events.getOrElse(id, Vector.empty).asInstanceOf[Vector[E]]

  def getLatestSnapshot[S](id: String): Option[S] =
    snapshots.get(id).map(_.asInstanceOf[S])

  def replayEvents[S, E](id: String, state: S, applyEvent: (S, E) => S): S =
    getEvents[E](id).foldLeft(state)(applyEvent)

  // テスト用にストアをクリアするメソッド
  def clear(): Unit = {
    events.clear()
    snapshots.clear()
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

  // 初期状態の復元（スナップショット＋イベント）
  private val latestSnapshot = InMemoryEventStore.getLatestSnapshot[S](persistenceId)
  private var currentState: S = latestSnapshot match {
    case Some(snapshot) =>
      ctx.log.debug(s"Recovered from snapshot for $persistenceId")
      InMemoryEventStore.replayEvents(persistenceId, snapshot, applyEvent)
    case None =>
      ctx.log.debug(s"Starting from initial state for $persistenceId")
      InMemoryEventStore.replayEvents(persistenceId, initialState, applyEvent)
  }

  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting event: {}", event)

    // イベントをメモリに保存
    InMemoryEventStore.addEvent(persistenceId, event)

    // 注: ここでapplyEventを使って状態を更新しない
    // 状態更新はすでにコマンドハンドラ内で行われている

    // コールバックを即時実行（永続化待ちがない）
    val behavior = onPersisted(event)

    // stashBufferが空ではない場合はunstashAll
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  override def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting events: {}", events)

    // イベントをメモリに保存
    InMemoryEventStore.addEvents(persistenceId, events)

    // 注: ここでapplyEventを使って状態を更新しない
    // 状態更新はすでにコマンドハンドラ内で行われている

    // コールバックを即時実行
    val behavior = onPersisted(events)

    // stashBufferが空ではない場合はunstashAll
    if (!stashBuffer.isEmpty) {
      stashBuffer.unstashAll(behavior)
    } else {
      behavior
    }
  }

  override def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("In-memory persisting snapshot: {}", snapshot)

    // スナップショットをメモリに保存
    InMemoryEventStore.saveSnapshot(persistenceId, snapshot)

    // 状態を更新（スナップショットの場合は直接更新する）
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

  // アクセサメソッド - テスト用
  def getState: S = currentState
}

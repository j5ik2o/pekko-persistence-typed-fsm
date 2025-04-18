package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

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

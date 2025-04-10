package com.github.j5ik2o.pekko.persistence.effector

import com.github.j5ik2o.pekko.persistence.effector.PersistenceStoreActor.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

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
    persistenceRef ! PersistEventSequence(events, adapter)
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
      // retentionCriteriaが設定されていれば適用
      config.retentionCriteria.foreach { retention =>
        // TODO: 実際のretentionポリシーの適用（古いスナップショットの削除など）
        ctx.log.debug("Applying retention policy: {}", retention)
      }

      persistenceRef ! PersistSnapshot(snapshot, adapter)
      Behaviors.receiveMessagePartial {
        case msg if unwrapPersistedSnapshot(msg).isDefined =>
          ctx.log.debug("Persisted snapshot: {}", msg)
          val state = unwrapPersistedSnapshot(msg).get
          stashBuffer.unstashAll(onPersisted(state))
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
        }

        stashBuffer.unstashAll(onPersisted(events.head))
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }

  override def persistEventsWithState(events: Seq[E], state: S, force: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting events with state: {}", events)
    persistenceRef ! PersistEventSequence(events, adapter)

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
        }

        stashBuffer.unstashAll(onPersisted(persistedEvents))
      case other =>
        ctx.log.debug("Stashing message: {}", other)
        stashBuffer.stash(other)
        Behaviors.same
    }
  }
}

package com.github.j5ik2o.pekko.persistence.effector

/**
 * スナップショット戦略を定義するトレイト
 * スナップショットを取得すべきかどうかを判断するための条件を表す
 */
sealed trait SnapshotCriteria[S, E] {
  /**
   * スナップショットを取得すべきかどうかを判断する
   *
   * @param event イベント
   * @param state 現在の状態
   * @param sequenceNumber シーケンス番号
   * @return スナップショットを取得すべき場合はtrue
   */
  def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean
}

/**
 * スナップショット戦略のファクトリメソッドとデフォルト実装を提供するコンパニオンオブジェクト
 */
object SnapshotCriteria {
  /**
   * イベントの内容に基づいてスナップショットを取得するかどうかを判断する戦略
   *
   * @param predicate スナップショットを取得すべきかどうかを判断する述語関数
   */
  final case class EventBased[S, E](
    predicate: (E, S, Long) => Boolean
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      predicate(event, state, sequenceNumber)
  }

  /**
   * イベント数に基づいてスナップショットを取得するかどうかを判断する戦略
   *
   * @param every N回のイベントごとにスナップショットを取得する
   */
  final case class CountBased[S, E](
    every: Int
  ) extends SnapshotCriteria[S, E] {
    require(every > 0, "every must be greater than 0")
    
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean =
      sequenceNumber % every == 0
  }

  /**
   * 複数の条件を組み合わせた戦略
   *
   * @param criteria 条件のリスト
   * @param requireAll trueの場合はすべての条件を満たす必要がある（AND条件）、falseの場合はいずれかの条件を満たせばよい（OR条件）
   */
  final case class Combined[S, E](
    criteria: Seq[SnapshotCriteria[S, E]],
    requireAll: Boolean = false
  ) extends SnapshotCriteria[S, E] {
    override def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean = {
      val results = criteria.map(_.shouldTakeSnapshot(event, state, sequenceNumber))
      if (requireAll) results.forall(identity) else results.exists(identity)
    }
  }

  /**
   * 常にスナップショットを取得する戦略
   */
  def always[S, E]: SnapshotCriteria[S, E] = 
    EventBased[S, E]((unused1, unused2, unused3) => true)
  
  /**
   * 特定のイベントタイプのときのみスナップショットを取得する戦略
   *
   * @param eventClass スナップショットを取得するイベントのクラス
   */
  def onEventType[S, E](eventClass: Class[?]): SnapshotCriteria[S, E] = 
    EventBased[S, E]((evt, unused, unused2) => eventClass.isInstance(evt))
  
  /**
   * N回のイベントごとにスナップショットを取得する戦略
   *
   * @param nth スナップショットを取得する間隔
   */
  def every[S, E](nth: Int): SnapshotCriteria[S, E] = 
    CountBased[S, E](nth)
}

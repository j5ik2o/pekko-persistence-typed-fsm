package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * スナップショットの保持ポリシーを定義するクラス
 */
final case class RetentionCriteria private (
  snapshotEvery: Option[Int] = scala.None,
  keepNSnapshots: Option[Int] = scala.None,
)

object RetentionCriteria {

  /**
   * デフォルトの保持ポリシー（設定なし）
   */
  val Empty: RetentionCriteria = RetentionCriteria()

  /**
   * N回のイベントごとにスナップショットを取得し、最新のN個のスナップショットを保持する
   *
   * @param numberOfEvents
   *   イベント数
   * @param keepNSnapshots
   *   保持するスナップショット数
   * @return
   *   RetentionCriteria
   */
  def snapshotEvery(numberOfEvents: Int, keepNSnapshots: Int = 2): RetentionCriteria = {
    require(numberOfEvents > 0, "numberOfEvents must be greater than 0")
    require(keepNSnapshots > 0, "keepNSnapshots must be greater than 0")

    RetentionCriteria(
      snapshotEvery = Some(numberOfEvents),
      keepNSnapshots = Some(keepNSnapshots),
    )
  }
}

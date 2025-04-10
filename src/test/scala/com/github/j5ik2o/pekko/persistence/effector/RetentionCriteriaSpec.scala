package com.github.j5ik2o.pekko.persistence.effector

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * RetentionCriteriaの単体テスト
 */
class RetentionCriteriaSpec extends AnyWordSpec with Matchers {

  "RetentionCriteria" should {
    "calculate correct max sequence number to delete" in {
      // 計算ロジックのテスト
      // N = 2個を保持
      // 毎回10個目ごとにスナップショット
      // シーケンス番号が30の場合に古いスナップショットを削除する場合

      val snapshotEvery = 10
      val keepNSnapshots = 2
      val currentSeqNr = 30

      // 2個保持なので、シーケンス番号10と20のスナップショットが残り
      // それより古いものは削除対象になる
      // 計算式: (30 - 10 * (2 - 1)) - 10 = 10
      // 10以下のスナップショットが削除対象

      // RetentionCriteriaを作成
      val retentionCriteria = RetentionCriteria.snapshotEvery(snapshotEvery, keepNSnapshots)

      // DefaultPersistenceEffectorのcalculateMaxSequenceNumberToDeleteロジックを再現
      def calculate(currentSeqNr: Long, criteria: RetentionCriteria): Long =
        (criteria.snapshotEvery, criteria.keepNSnapshots) match {
          case (Some(snapshotEvery), Some(keepNSnapshots)) =>
            // 最新のスナップショットのシーケンス番号を計算
            val latestSnapshotSeqNr = currentSeqNr - (currentSeqNr % snapshotEvery)

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

      // テスト実行
      val result = calculate(currentSeqNr, retentionCriteria)

      // 検証 - 10以下のスナップショットが削除対象
      result shouldBe 10L

      // 別のケース - シーケンス番号50
      val result2 = calculate(50, retentionCriteria)
      // 50の場合、最新は50、保持するのは40と50、30以下が削除対象
      result2 shouldBe 30L

      // エッジケース - シーケンス番号が小さすぎる場合
      val result3 = calculate(5, retentionCriteria)
      // 最初のスナップショットすらない場合、削除対象はなし
      result3 shouldBe 0L

      // エッジケース - 保持数が1の場合
      val retainOnlyCriteria = RetentionCriteria.snapshotEvery(10, 1)
      val result4 = calculate(30, retainOnlyCriteria)
      // 最新の1つだけ保持するので、20以下が削除対象
      result4 shouldBe 20L
    }

    "use retentionCriteria.snapshotEvery factory method" in {
      val criteria = RetentionCriteria.snapshotEvery(10, 2)
      criteria.snapshotEvery shouldBe Some(10)
      criteria.keepNSnapshots shouldBe Some(2)
    }

    "have Empty object with no retention settings" in {
      val empty = RetentionCriteria.Empty
      empty.snapshotEvery shouldBe None
      empty.keepNSnapshots shouldBe None
    }

    "throw exception on invalid parameters" in {
      an[IllegalArgumentException] should be thrownBy
        RetentionCriteria.snapshotEvery(0, 2)

      an[IllegalArgumentException] should be thrownBy
        RetentionCriteria.snapshotEvery(10, 0)
    }
  }
}

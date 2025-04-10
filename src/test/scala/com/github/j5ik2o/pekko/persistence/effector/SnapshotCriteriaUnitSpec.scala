package com.github.j5ik2o.pekko.persistence.effector

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.language.adhocExtensions

/**
 * SnapshotCriteriaの単体テスト
 */
class SnapshotCriteriaUnitSpec extends AnyWordSpec with Matchers {

  "SnapshotCriteria" should {
    // 単純な単体テスト: SnapshotCriteriaの各実装を直接テスト
    "correctly evaluate count-based snapshot criteria" in {
      val countBasedCriteria = SnapshotCriteria.every[TestState, TestEvent](3)

      // シーケンス番号が3の倍数のイベントでスナップショットを取るか検証
      countBasedCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 1)
        .shouldBe(false)
      countBasedCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 2)
        .shouldBe(false)
      countBasedCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 3)
        .shouldBe(true)
      countBasedCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 4)
        .shouldBe(false)
      countBasedCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 6)
        .shouldBe(true)
    }

    "correctly evaluate event-type based snapshot criteria" in {
      val eventTypeCriteria =
        SnapshotCriteria.onEventType[TestState, TestEvent](classOf[TestEvent.TestEventB])

      // TestEventBタイプのイベントでのみスナップショットを取るか検証
      eventTypeCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 1)
        .shouldBe(false)
      eventTypeCriteria.shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 2).shouldBe(true)
    }

    "correctly evaluate combined criteria with OR logic" in {
      val combinedOrCriteria = SnapshotCriteria.Combined[TestState, TestEvent](
        Seq(
          SnapshotCriteria.every[TestState, TestEvent](3),
          SnapshotCriteria.onEventType[TestState, TestEvent](classOf[TestEvent.TestEventB]),
        ),
        requireAll = false, // OR条件
      )

      // OR条件の検証: 3の倍数のシーケンス番号 OR TestEventBタイプ
      combinedOrCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 1)
        .shouldBe(false)
      combinedOrCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 2)
        .shouldBe(true) // イベントタイプが合致
      combinedOrCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 3)
        .shouldBe(true) // シーケンス番号が合致
      combinedOrCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 4)
        .shouldBe(false)
    }

    "correctly evaluate combined criteria with AND logic" in {
      val combinedAndCriteria = SnapshotCriteria.Combined[TestState, TestEvent](
        Seq(
          SnapshotCriteria.every[TestState, TestEvent](2),
          SnapshotCriteria.onEventType[TestState, TestEvent](classOf[TestEvent.TestEventB]),
        ),
        requireAll = true, // AND条件
      )

      // AND条件の検証: 2の倍数のシーケンス番号 AND TestEventBタイプ
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 1)
        .shouldBe(false)
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 2)
        .shouldBe(false) // シーケンス番号だけ合致
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 3)
        .shouldBe(false) // イベントタイプだけ合致
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 2)
        .shouldBe(true) // 両方合致
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 4)
        .shouldBe(true) // 両方合致
    }
  }
}

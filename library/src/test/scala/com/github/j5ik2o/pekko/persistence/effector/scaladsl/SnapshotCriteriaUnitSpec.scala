package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import com.github.j5ik2o.pekko.persistence.effector.{TestEvent, TestState}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.language.adhocExtensions

/**
 * Unit test for SnapshotCriteria
 */
class SnapshotCriteriaUnitSpec extends AnyWordSpec with Matchers {

  "SnapshotCriteria" should {
    // Simple unit test: directly test each implementation of SnapshotCriteria
    "correctly evaluate count-based snapshot criteria" in {
      val countBasedCriteria = SnapshotCriteria.every[TestState, TestEvent](3)

      // Verify taking snapshots for events with sequence numbers that are multiples of 3
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

      // Verify taking snapshots only for TestEventB type events
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
        requireAll = false, // OR condition
      )

      // OR condition verification: sequence number multiple of 3 OR TestEventB type
      combinedOrCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 1)
        .shouldBe(false)
      combinedOrCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 2)
        .shouldBe(true) // Event type matches
      combinedOrCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 3)
        .shouldBe(true) // Sequence number matches
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
        requireAll = true, // AND condition
      )

      // AND condition verification: sequence number multiple of 2 AND TestEventB type
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 1)
        .shouldBe(false)
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventA("test"), TestState(), 2)
        .shouldBe(false) // Only sequence number matches
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 3)
        .shouldBe(false) // Only event type matches
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 2)
        .shouldBe(true) // Both match
      combinedAndCriteria
        .shouldTakeSnapshot(TestEvent.TestEventB(42), TestState(), 4)
        .shouldBe(true) // Both match
    }
  }
}

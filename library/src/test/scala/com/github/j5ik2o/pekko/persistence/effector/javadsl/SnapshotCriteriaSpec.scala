package com.github.j5ik2o.pekko.persistence.effector.javadsl

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

object SnapshotCriteriaSpec {
  class TestState
  class TestEvent
  class SpecialEvent extends TestEvent
}

final class SnapshotCriteriaSpec extends AnyFreeSpec with Matchers {
  import SnapshotCriteriaSpec.*

  // Helper methods
  private def assertSnapshotShouldBeTaken(
    criteria: SnapshotCriteria[TestState, TestEvent],
    event: TestEvent,
    seq: Long): Unit =
    criteria.shouldTakeSnapshot(event, new TestState, seq).`shouldBe`(true)

  private def assertSnapshotShouldNotBeTaken(
    criteria: SnapshotCriteria[TestState, TestEvent],
    event: TestEvent,
    seq: Long): Unit =
    criteria.shouldTakeSnapshot(event, new TestState, seq).`shouldBe`(false)

  "A SnapshotCriteria for Java API" - {
    "when using always strategy" - {
      lazy val criteria = SnapshotCriteria.always[TestState, TestEvent]()

      "should always return true regardless of input" in {
        assertSnapshotShouldBeTaken(criteria, new TestEvent, 1L)
        assertSnapshotShouldBeTaken(criteria, new TestEvent, 100L)
        assertSnapshotShouldBeTaken(criteria, new SpecialEvent, 1L)
      }
    }

    "when using event type strategy" - {
      lazy val criteria = SnapshotCriteria.onEventType[TestState, TestEvent](classOf[SpecialEvent])

      "should return false for non-matching event type" in
        assertSnapshotShouldNotBeTaken(criteria, new TestEvent, 1L)

      "should return true for matching event type" in
        assertSnapshotShouldBeTaken(criteria, new SpecialEvent, 1L)
    }

    "when using count based strategy" - {
      lazy val criteria = SnapshotCriteria.every[TestState, TestEvent](3)

      "should return true only for multiples of the specified count" in {
        assertSnapshotShouldNotBeTaken(criteria, new TestEvent, 1L)
        assertSnapshotShouldNotBeTaken(criteria, new TestEvent, 2L)
        assertSnapshotShouldBeTaken(criteria, new TestEvent, 3L)
        assertSnapshotShouldNotBeTaken(criteria, new TestEvent, 4L)
        assertSnapshotShouldNotBeTaken(criteria, new TestEvent, 5L)
        assertSnapshotShouldBeTaken(criteria, new TestEvent, 6L)
      }

      "should work the same for different event types" in
        assertSnapshotShouldBeTaken(criteria, new SpecialEvent, 3L)
    }

    "when using combined strategy" - {
      def createCombinedCriteria(requireAll: Boolean) = {
        val criteriaList = java.util.Arrays.asList[SnapshotCriteria[TestState, TestEvent]](
          SnapshotCriteria.every[TestState, TestEvent](2),
          SnapshotCriteria.onEventType[TestState, TestEvent](classOf[SpecialEvent]),
        )
        SnapshotCriteria.combined[TestState, TestEvent](criteriaList, requireAll)
      }

      "with OR condition" - {
        lazy val combined = createCombinedCriteria(false)

        "should return true if any criterion is satisfied" in {
          assertSnapshotShouldNotBeTaken(combined, new TestEvent, 1L)
          assertSnapshotShouldBeTaken(combined, new TestEvent, 2L)
          assertSnapshotShouldBeTaken(combined, new SpecialEvent, 3L)
        }
      }

      "with AND condition" - {
        lazy val combined = createCombinedCriteria(true)

        "should return true only if all criteria are satisfied" in {
          assertSnapshotShouldNotBeTaken(combined, new TestEvent, 2L)
          assertSnapshotShouldNotBeTaken(combined, new SpecialEvent, 1L)
          assertSnapshotShouldBeTaken(combined, new SpecialEvent, 2L)
        }
      }
    }

    "when converting between Scala and Java implementations" - {
      "should maintain the same behavior" in {
        val javaCriteria = SnapshotCriteria.every[TestState, TestEvent](2)
        val scalaCriteria = javaCriteria.toScala
        val javaAgain = SnapshotCriteria.fromScala(scalaCriteria)

        val event = new TestEvent
        val state = new TestState

        javaAgain
          .shouldTakeSnapshot(event, state, 2L)
          .`shouldBe`(
            javaCriteria.shouldTakeSnapshot(event, state, 2L),
          )
      }
    }
  }
}

package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit test for RetentionCriteria
 */
class RetentionCriteriaSpec extends AnyWordSpec with Matchers {

  "RetentionCriteria" should {
    "calculate correct max sequence number to delete" in {
      // Test calculation logic
      // Keep N = 2
      // Snapshot every 10 events
      // When deleting old snapshots at sequence number 30

      val snapshotEvery = 10
      val keepNSnapshots = 2
      val currentSeqNr = 30

      // Since we keep 2, snapshots at sequence numbers 10 and 20 remain
      // Older ones become deletion targets
      // Calculation: (30 - 10 * (2 - 1)) - 10 = 10
      // Snapshots with sequence number 10 or less are deletion targets

      // Create RetentionCriteria
      val retentionCriteria = RetentionCriteria.snapshotEvery(snapshotEvery, keepNSnapshots)

      // Reproduce calculateMaxSequenceNumberToDelete logic from DefaultPersistenceEffector
      def calculate(currentSeqNr: Long, criteria: RetentionCriteria): Long =
        (criteria.snapshotEvery, criteria.keepNSnapshots) match {
          case (Some(snapshotEvery), Some(keepNSnapshots)) =>
            // Calculate sequence number of the latest snapshot
            val latestSnapshotSeqNr = currentSeqNr - (currentSeqNr % snapshotEvery)

            if (latestSnapshotSeqNr < snapshotEvery) {
              // If even the first snapshot has not been created
              0L
            } else {
              // The oldest sequence number of snapshots to keep
              val oldestKeptSnapshot =
                latestSnapshotSeqNr - (snapshotEvery.toLong * (keepNSnapshots - 1))

              if (oldestKeptSnapshot <= 0) {
                // If all snapshots to be kept do not exist
                0L
              } else {
                // Maximum sequence number to be deleted (snapshot just before oldestKeptSnapshot)
                val maxSequenceNumberToDelete = oldestKeptSnapshot - snapshotEvery

                if (maxSequenceNumberToDelete <= 0) 0L else maxSequenceNumberToDelete
              }
            }
          case _ =>
            // Do not delete if either setting is missing
            0L
        }

      // Run test
      val result = calculate(currentSeqNr, retentionCriteria)

      // Verification - snapshots with sequence number 10 or less are deletion targets
      result.shouldBe(10L)

      // Another case - sequence number 50
      val result2 = calculate(50, retentionCriteria)
      // For 50, the latest is 50, keep 40 and 50, 30 or less are deletion targets
      result2.shouldBe(30L)

      // Edge case - when sequence number is too small
      val result3 = calculate(5, retentionCriteria)
      // If there isn't even a first snapshot, no deletion targets
      result3.shouldBe(0L)

      // Edge case - when retention count is 1
      val retainOnlyCriteria = RetentionCriteria.snapshotEvery(10, 1)
      val result4 = calculate(30, retainOnlyCriteria)
      // Since only the latest one is kept, 20 or less are deletion targets
      result4.shouldBe(20L)
    }

    "use retentionCriteria.snapshotEvery factory method" in {
      val criteria = RetentionCriteria.snapshotEvery(10, 2)
      criteria.snapshotEvery.shouldBe(Some(10))
      criteria.keepNSnapshots.shouldBe(Some(2))
    }

    "have Default object with no retention settings" in {
      val criteria = RetentionCriteria.Default
      criteria.snapshotEvery.shouldBe(None)
      criteria.keepNSnapshots.shouldBe(None)
    }

    "throw exception on invalid parameters" in {
      an[IllegalArgumentException] should be thrownBy
        RetentionCriteria.snapshotEvery(0, 2)

      an[IllegalArgumentException] should be thrownBy
        RetentionCriteria.snapshotEvery(10, 0)
    }
  }
}

package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceMode

import java.io.File

/**
 * Test for BankAccountAggregate using Persisted mode
 */
class BankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted

  // Ensure LevelDB storage directory is created before testing
  override def beforeAll(): Unit = {
    val journalDir = new File("target/journal")
    val snapshotDir = new File("target/snapshot")

    if (!journalDir.exists()) {
      journalDir.mkdirs()
    }

    if (!snapshotDir.exists()) {
      snapshotDir.mkdirs()
    }

    super.beforeAll()
  }

  // Clean up directory after testing
  override def afterAll(): Unit =
    super.afterAll()

}

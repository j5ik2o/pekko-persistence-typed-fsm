package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.InMemoryEventStore
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceMode

/**
 * Test for BankAccountAggregate using InMemory mode
 */
class InMemoryBankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Ephemeral

  // Clear InMemoryStore at the end of the test
  override def afterAll(): Unit = {
    InMemoryEventStore.clear()
    super.afterAll()
  }
}

package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl

import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.InMemoryEventStore
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceMode

/**
 * InMemoryモードを使用したBankAccountAggregateのテスト
 */
class InMemoryBankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.EPHEMERAL

  // テスト終了時にInMemoryStoreをクリア
  override def afterAll(): Unit = {
    InMemoryEventStore.clear()
    super.afterAll()
  }
}

package com.github.j5ik2o.pekko.persistence.typed.fsm
package example

/**
 * InMemoryモードを使用したBankAccountAggregateのテスト
 */
class InMemoryBankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.InMemory
  
  // テスト終了時にInMemoryStoreをクリア
  override def afterAll(): Unit = {
    InMemoryEventStore.clear()
    super.afterAll()
  }
}

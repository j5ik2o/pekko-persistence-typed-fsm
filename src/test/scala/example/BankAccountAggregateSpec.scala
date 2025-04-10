package com.github.j5ik2o.pekko.persistence.effector
package example

/**
 * Persistedモードを使用したBankAccountAggregateのテスト
 */
class BankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted
}

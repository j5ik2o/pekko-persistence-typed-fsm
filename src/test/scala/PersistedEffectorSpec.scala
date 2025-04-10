package com.github.j5ik2o.pekko.persistence.typed.fsm

/**
 * Persistedモードを使用したPersistenceEffectorのテスト
 */
class PersistedEffectorSpec extends PersistenceEffectorTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted
}

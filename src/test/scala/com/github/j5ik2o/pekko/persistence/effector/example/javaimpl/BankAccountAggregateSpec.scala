package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl

import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceMode

import java.io.File

/**
 * Persistedモードを使用したBankAccountAggregateのテスト
 */
class BankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.PERSISTENCE

  // テスト前にLevelDBの保存ディレクトリを確実に作成
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

  // テスト後にディレクトリをクリーンアップ
  override def afterAll(): Unit =
    super.afterAll()

}

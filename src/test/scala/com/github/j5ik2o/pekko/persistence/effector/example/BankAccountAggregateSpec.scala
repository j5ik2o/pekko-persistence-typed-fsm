package com.github.j5ik2o.pekko.persistence.effector.example

import com.github.j5ik2o.pekko.persistence.effector.PersistenceMode

import java.io.File
import java.util.UUID

/**
 * Persistedモードを使用したBankAccountAggregateのテスト
 */
class BankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted

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

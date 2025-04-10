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

  "BankAccountAggregate with Persisted mode" should {
    "maintain state after stop and restart with multiple actions" in {
      val accountId = BankAccountId(UUID.randomUUID())

      // 最初のアクターを作成して状態を構築
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // 預金
      val depositAmount = Money(50000, Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[DepositCashReply.Succeeded]

      // もう一度預金
      val depositAmount2 = Money(20000, Money.JPY)
      val depositProbe2 = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(
        accountId,
        depositAmount2,
        depositProbe2.ref)
      depositProbe2.expectMessageType[DepositCashReply.Succeeded]

      // スナップショットを作成するために明示的に停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply.Succeeded]

      // 2番目のアクターを作成 - この時点でPersistenceStoreActorのreceiveRecoverが呼ばれる
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認 - 前のアクターの状態が復元されていることを確認
      val expectedBalance = Money(70000, Money.JPY) // 50000 + 20000
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply.Succeeded]
      balanceResponse.balance shouldBe expectedBalance

      // アクター再起動後も正常に操作できることを確認
      val withdrawAmount = Money(10000, Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor2 ! BankAccountCommand.WithdrawCash(
        accountId,
        withdrawAmount,
        withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[WithdrawCashReply.Succeeded]
      withdrawResponse.amount shouldBe withdrawAmount

      // 最終残高確認
      val finalBalanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, finalBalanceProbe.ref)

      val finalResponse = finalBalanceProbe.expectMessageType[GetBalanceReply.Succeeded]
      finalResponse.balance shouldBe Money(60000, Money.JPY) // 70000 - 10000
    }

    "maintain state after stop and restart" in {
      val accountId = BankAccountId(UUID.randomUUID())

      // 最初のアクターを作成して状態を構築
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // 預金
      val depositAmount = Money(50000, Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[DepositCashReply.Succeeded]

      // スナップショットを作成するために明示的に停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply.Succeeded]

      // アクターを再起動（この時点でreceiveRecoverが呼ばれる）
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認 - 前のアクターの状態が復元されていることを確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply.Succeeded]
      balanceResponse.balance shouldBe depositAmount
    }

    "restore initial state after stop and restart" in {
      val accountId = BankAccountId(UUID.randomUUID())

      // 最初のアクターを作成して初期状態を構築
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // アクターを停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply.Succeeded]

      // アクターを再起動（この時点でreceiveRecoverが呼ばれる必要がある）
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認 - 初期状態が正しく復元されていることを確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply.Succeeded]
      // 口座作成のみで入金はしていないので、残高は0円のはず
      balanceResponse.balance shouldBe Money(0, Money.JPY)

      // コメント: receiveRecoverが正しく呼ばれていれば、状態が復元されます。
      // もし呼ばれていなければ、このテストは失敗します。
    }
  }
}

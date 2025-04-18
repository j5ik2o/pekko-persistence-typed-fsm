package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl

import com.github.j5ik2o.pekko.persistence.effector.TestConfig
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceMode
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.Behavior
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.math.BigDecimal as JavaBigDecimal
import java.util.UUID
import _root_.scala.concurrent.duration.*

/**
 * BankAccountAggregateのテスト基底クラス 具体的なモード（Persisted/InMemory）はサブクラスで指定する
 */
abstract class BankAccountAggregateTestBase
  extends ScalaTestWithActorTestKit(TestConfig.config)
  with AnyWordSpecLike
  with Matchers
  with Eventually
  with BeforeAndAfterAll {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 10.seconds, interval = 100.millis)

  // サブクラスで実装するメソッド - テスト対象のPersistenceMode
  def persistenceMode: PersistenceMode

  // BankAccountAggregateを生成するヘルパーメソッド
  def createBankAccountAggregate(accountId: BankAccountId): Behavior[BankAccountCommand] =
    BankAccountAggregate.create(accountId, persistenceMode)

  s"BankAccountAggregate with ${persistenceMode} mode" should {
    "create a new bank account successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      val probe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, probe.ref)

      val response = probe.expectMessageType[CreateReply]
      response shouldBe CreateReply.SUCCEEDED
      response.getAggregateId shouldBe accountId
    }

    "deposit cash successfully" ignore {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      val depositProbe = createTestProbe[DepositCashReply]()

      for { _ <- 1 to 10 } {
        // 預金
        val depositAmount = Money.of(JavaBigDecimal.valueOf(10000), Money.JPY)
        bankAccountActor ! BankAccountCommand.DepositCash(
          accountId,
          depositAmount,
          depositProbe.ref)

        val depositResponse = depositProbe.expectMessageType[DepositCashReply]
        depositResponse shouldBe DepositCashReply.SUCCEEDED
        depositResponse.getAggregateId shouldBe accountId
        depositResponse.getAmount shouldBe depositAmount
      }
      // 口座の停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply]

      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply]
      // balanceResponse.balance shouldBe depositAmount
    }

    "withdraw cash successfully" ignore {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      // 預金
      val depositAmount = Money.of(JavaBigDecimal.valueOf(10000), Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[DepositCashReply]

      // 引き出し
      val withdrawAmount = Money.of(JavaBigDecimal.valueOf(3000), Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(
        accountId,
        withdrawAmount,
        withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[WithdrawCashReply]
      withdrawResponse.getAggregateId shouldBe accountId
      withdrawResponse.getAmount shouldBe withdrawAmount

      // 残高確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply]
      balanceResponse.getBalance shouldBe Money.of(JavaBigDecimal.valueOf(7000), Money.JPY)
    }

    "get balance successfully" ignore {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      // 初期残高確認
      val initialBalanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, initialBalanceProbe.ref)

      val initialBalanceResponse = initialBalanceProbe.expectMessageType[GetBalanceReply]
      initialBalanceResponse.getBalance shouldBe Money.of(JavaBigDecimal.valueOf(0), Money.JPY)
    }

    "fail to withdraw when insufficient funds" ignore {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      // 預金額以上の引き出し試行
      val withdrawAmount = Money.of(JavaBigDecimal.valueOf(1000), Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(
        accountId,
        withdrawAmount,
        withdrawProbe.ref)

      val failedResponse = withdrawProbe.expectMessageType[WithdrawCashReply]
      failedResponse.getAggregateId shouldBe accountId
      failedResponse.getError shouldBe BankAccountError.LIMIT_OVER_ERROR
    }

    "fail to deposit when over limit" ignore {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      // 上限を超える預金試行
      val depositAmount = Money.of(JavaBigDecimal.valueOf(150000), Money.JPY) // 上限は100000円
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)

      val failedResponse = depositProbe.expectMessageType[DepositCashReply]
      failedResponse.getAggregateId shouldBe accountId
      failedResponse.getError shouldBe BankAccountError.LIMIT_OVER_ERROR
    }

    "maintain state after stop and restart with multiple actions" ignore {
      val accountId = BankAccountId(UUID.randomUUID())

      // 最初のアクターを作成して状態を構築
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      // 預金
      val depositAmount = Money.of(JavaBigDecimal.valueOf(50000), Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[DepositCashReply]

      // もう一度預金
      val depositAmount2 = Money.of(JavaBigDecimal.valueOf(20000), Money.JPY)
      val depositProbe2 = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(
        accountId,
        depositAmount2,
        depositProbe2.ref)
      depositProbe2.expectMessageType[DepositCashReply]

      // スナップショットを作成するために明示的に停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply]

      // 2番目のアクターを作成 - この時点でPersistenceStoreActorのreceiveRecoverが呼ばれる
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認 - 前のアクターの状態が復元されていることを確認
      val expectedBalance = Money.of(JavaBigDecimal.valueOf(70000), Money.JPY) // 50000 + 20000
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply]
      balanceResponse.getBalance shouldBe expectedBalance

      // アクター再起動後も正常に操作できることを確認
      val withdrawAmount = Money.of(JavaBigDecimal.valueOf(10000), Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor2 ! BankAccountCommand.WithdrawCash(
        accountId,
        withdrawAmount,
        withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[WithdrawCashReply]
      withdrawResponse.getAmount shouldBe withdrawAmount

      // 最終残高確認
      val finalBalanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, finalBalanceProbe.ref)

      val finalResponse = finalBalanceProbe.expectMessageType[GetBalanceReply]
      finalResponse.getBalance shouldBe Money.of(
        JavaBigDecimal.valueOf(60000),
        Money.JPY,
      ) // 70000 - 10000
    }

    "maintain state after stop and restart" ignore {
      val accountId = BankAccountId(UUID.randomUUID())

      // 最初のアクターを作成して状態を構築
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      // 預金
      val depositAmount = Money.of(JavaBigDecimal.valueOf(50000), Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[DepositCashReply]

      // スナップショットを作成するために明示的に停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply]

      // アクターを再起動（この時点でreceiveRecoverが呼ばれる）
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認 - 前のアクターの状態が復元されていることを確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply]
      balanceResponse.getBalance shouldBe depositAmount
    }

    "restore initial state after stop and restart" in {
      val accountId = BankAccountId(UUID.randomUUID())

      // 最初のアクターを作成して初期状態を構築
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply]

      // アクターを停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply]

      // アクターを再起動（この時点でreceiveRecoverが呼ばれる必要がある）
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認 - 初期状態が正しく復元されていることを確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply]
      // 口座作成のみで入金はしていないので、残高は0円のはず
      balanceResponse.getBalance shouldBe Money.of(JavaBigDecimal.valueOf(0), Money.JPY)

      // コメント: receiveRecoverが正しく呼ばれていれば、状態が復元されます。
      // もし呼ばれていなければ、このテストは失敗します。
    }
  }
  // 追加のモード特有のテストケースはサブクラスで追加
}

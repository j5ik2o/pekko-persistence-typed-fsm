package com.github.j5ik2o.pekko.persistence.effector.example

import com.github.j5ik2o.pekko.persistence.effector.{PersistenceMode, TestConfig}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.Behavior
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.*

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
    BankAccountAggregate(accountId, persistenceMode)

  s"BankAccountAggregate with ${persistenceMode} mode" should {
    "create a new bank account successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      val probe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, probe.ref)

      val response = probe.expectMessageType[CreateReply.Succeeded]
      response.aggregateId shouldBe accountId
    }

    "deposit cash successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // 預金
      val depositAmount = Money(10000, Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)

      val depositResponse = depositProbe.expectMessageType[DepositCashReply.Succeeded]
      depositResponse.aggregateId shouldBe accountId
      depositResponse.amount shouldBe depositAmount

      // 口座の停止
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[StopReply.Succeeded]

      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // 残高確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply.Succeeded]
      balanceResponse.balance shouldBe depositAmount
    }

    "withdraw cash successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // 預金
      val depositAmount = Money(10000, Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[DepositCashReply.Succeeded]

      // 引き出し
      val withdrawAmount = Money(3000, Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(
        accountId,
        withdrawAmount,
        withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[WithdrawCashReply.Succeeded]
      withdrawResponse.aggregateId shouldBe accountId
      withdrawResponse.amount shouldBe withdrawAmount

      // 残高確認
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[GetBalanceReply.Succeeded]
      balanceResponse.balance shouldBe Money(7000, Money.JPY)
    }

    "get balance successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // 初期残高確認
      val initialBalanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, initialBalanceProbe.ref)

      val initialBalanceResponse = initialBalanceProbe.expectMessageType[GetBalanceReply.Succeeded]
      initialBalanceResponse.balance shouldBe Money(0, Money.JPY)
    }

    "fail to withdraw when insufficient funds" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // 預金額以上の引き出し試行
      val withdrawAmount = Money(1000, Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(
        accountId,
        withdrawAmount,
        withdrawProbe.ref)

      val failedResponse = withdrawProbe.expectMessageType[WithdrawCashReply.Failed]
      failedResponse.aggregateId shouldBe accountId
      failedResponse.error shouldBe BankAccountError.LimitOverError
    }

    "fail to deposit when over limit" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // 口座作成
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CreateReply.Succeeded]

      // 上限を超える預金試行
      val depositAmount = Money(150000, Money.JPY) // 上限は100000円
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)

      val failedResponse = depositProbe.expectMessageType[DepositCashReply.Failed]
      failedResponse.aggregateId shouldBe accountId
      failedResponse.error shouldBe BankAccountError.LimitOverError
    }
  }

  // 追加のモード特有のテストケースはサブクラスで追加
}

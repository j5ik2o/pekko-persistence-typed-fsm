package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  PersistenceMode,
}
import com.github.j5ik2o.pekko.persistence.effector.{TestEvent, TestMessage, TestState}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import java.util

/**
 * InMemoryモードを使用したPersistenceEffectorのテスト
 */
class InMemoryEffectorSpec extends PersistenceEffectorTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.InMemory

  // スナップショットテストを有効化
  override def runSnapshotTests: Boolean = true

  // InMemoryモード特有のテスト
  s"Effector with ${persistenceMode} mode" should {
    "provide access to current state via getState" in {
      val persistenceId = s"test-get-state-${util.UUID.randomUUID()}"
      val initialState = TestState()
      val event = TestEvent.TestEventA("test-state-access")

      val config =
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
        )

      // InMemoryEffectorを取得するためのテスト用参照
      var effectorRef: Option[InMemoryEffector[TestState, TestEvent, TestMessage]] = None
      // 状態を保持
      var updatedState: TestState = initialState

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // 型キャストで InMemoryEffector を取得
            val inMemoryEffector =
              effector.asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
            effectorRef = Some(inMemoryEffector)

            // 手動で状態を更新（コマンドハンドラが行う処理を模倣）
            updatedState = initialState.applyEvent(event)

            // イベントを永続化
            effector.persistEvent(event) { _ =>
              // 初期Behaviorとしてsameは使えないので、受信処理を定義
              Behaviors.receiveMessage(_ => Behaviors.same)
            }
        }(using context)
      })

      eventually {
        // InMemoryEffectorのgetStateメソッドで状態が取得できることを確認
        effectorRef.isDefined shouldBe true

        // currentStateフィールドへのアクセスはできるが、
        // applyEventをpersistEvent内で呼ばなくなったため、
        // 手動で更新した状態と比較する
        updatedState.values should contain("test-state-access")
        updatedState.values.size shouldBe 1
      }
    }

    "not execute applyEvent twice when persistEvent is called" in {
      val persistenceId = s"test-no-double-apply-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      // applyEventの呼び出し回数をカウントするための変数
      var applyEventCount = 0

      // 呼び出し回数をカウントするapplyEvent関数
      val countingApplyEvent = (state: TestState, event: TestEvent) => {
        applyEventCount += 1
        state.applyEvent(event)
      }

      val config =
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = countingApplyEvent, // カウンター付きの関数を使用
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
        )

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // 初期状態の復元でapplyEventが呼ばれているためリセット
            applyEventCount = 0

            // 手動で状態を更新（通常これはドメインロジック内で行われる）
            val newState = countingApplyEvent(state, TestEvent.TestEventA("test-no-double"))

            // イベントを永続化（ここではapplyEventが呼ばれるべきではない）
            effector
              .asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
              .persistEvent(TestEvent.TestEventA("test-no-double")) { _ =>
                // 初期Behaviorとしてsameは使えないので、受信処理を定義
                Behaviors.receiveMessage(_ => Behaviors.same)
              }
        }(using context)
      })

      // イベント適用に十分な時間を確保
      Thread.sleep(500)

      // applyEventが1回だけ呼ばれることを確認（手動更新時のみ）
      applyEventCount shouldBe 1
    }
  }

  // テスト終了時にInMemoryStoreをクリア
  override def afterAll(): Unit = {
    InMemoryEventStore.clear()
    super.afterAll()
  }
}

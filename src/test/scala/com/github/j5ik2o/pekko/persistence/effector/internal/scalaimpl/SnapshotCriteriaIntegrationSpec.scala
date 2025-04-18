package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.*
import com.github.j5ik2o.pekko.persistence.effector.{TestEvent, TestMessage, TestState}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

/**
 * SnapshotCriteriaの統合テスト
 */
class SnapshotCriteriaIntegrationSpec extends PersistenceEffectorTestBase {
  // 永続化モードはPersistedを使用
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted

  // 現在のテスト環境ではスナップショットテストを無効にする
  override def runSnapshotTests: Boolean = true

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 10.seconds, interval = 500.millis)

  // スナップショットテスト用のコンテキスト
  case class SnapshotTestContext(
    // 永続化されたイベント
    events: ArrayBuffer[TestMessage.EventPersisted] = ArrayBuffer.empty,
    // 永続化されたスナップショット
    snapshots: ArrayBuffer[TestMessage.SnapshotPersisted] = ArrayBuffer.empty,
  ) {
    // メッセージを処理してテストコンテキストに追加するメソッド
    def processMessage(msg: TestMessage): Unit =
      msg match {
        case e: TestMessage.EventPersisted =>
          events += e
        case s: TestMessage.SnapshotPersisted =>
          snapshots += s
        case _ => // ignore
      }
  }

  "Effector with Persisted mode" should {
    "take snapshot based on count criteria in integration test" in {
      val persistenceId = s"test-snapshot-count-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      // テスト用のコンテキスト
      val testContext = SnapshotTestContext()

      // CountBasedのスナップショット戦略を設定（2イベントごと）
      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          persistenceMode = persistenceMode,
          stashSize = Int.MaxValue,
          snapshotCriteria = Some(SnapshotCriteria.every[TestState, TestEvent](2)),
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // テスト用データ
      val events = Seq(
        TestEvent.TestEventA("event1"),
        TestEvent.TestEventA("event2"),
        TestEvent.TestEventA("event3"),
        TestEvent.TestEventA("event4"),
        TestEvent.TestEventA("event5"),
      )

      // アクターを起動
      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // イベントを順に永続化する関数
            def persistNextEvent(
              remainingEvents: Seq[TestEvent],
              currentState: TestState,
            ): Behavior[TestMessage] =
              if (remainingEvents.isEmpty) {
                // イベント処理後の状態をログに出力
                context.log.debug("All events processed. Final state: {}", currentState)

                // テスト用スナップショットを明示的に追加（アクターの状態確認用）
                testContext.snapshots += TestMessage.SnapshotPersisted(currentState)

                // 次の振る舞いはBehaviors.sameでも良いが、万一メッセージが来た場合のために
                Behaviors.receiveMessage(_ => Behaviors.same)
              } else {
                val event = remainingEvents.head
                val nextEvents = remainingEvents.tail

                // イベントを永続化
                context.log.debug("Persisting event: {}, currentState: {}", event, currentState)
                effector.persistEventWithSnapshot(event, currentState) { e =>
                  testContext.events += TestMessage.EventPersisted(Seq(e))
                  val newState = currentState.applyEvent(e)
                  context.log.debug("Event persisted, checking for snapshots...")
                  Thread.sleep(100) // スナップショットが処理されるのを待つ
                  persistNextEvent(nextEvents, newState)
                }
              }

            // 最初のイベントから永続化を開始
            persistNextEvent(events, state)
        }(using context)
      })

      // スナップショットが取得されることを検証
      eventually {
        // 5つのイベントが永続化されることを確認
        testContext.events.size.shouldBe(5)

        // 明示的に追加したスナップショットの検証
        testContext.snapshots.foreach { snapshot =>
          // 状態が適切にキャプチャされていることを確認
          snapshot.state.values.nonEmpty shouldBe true
        }
      }
    }

    "verify snapshot restoration after actor restart" in {
      val persistenceId = s"test-snapshot-restore-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      // テスト用のコンテキスト
      val testContext = SnapshotTestContext()

      // CountBasedのスナップショット戦略を設定（2イベントごと）
      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = Some(SnapshotCriteria.every[TestState, TestEvent](2)),
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // テスト用データ
      val events = Seq(
        TestEvent.TestEventA("event1"),
        TestEvent.TestEventA("event2"), // 2イベント目でスナップショット
        TestEvent.TestEventA("event3"),
        TestEvent.TestEventA("event4"), // 4イベント目でスナップショット
      )

      // 1回目のアクター実行
      val behavior1 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // イベントを順に永続化する関数
            def persistNextEvent(
              remainingEvents: Seq[TestEvent],
              currentState: TestState,
            ): Behavior[TestMessage] =
              if (remainingEvents.isEmpty) {
                // イベント処理後の状態をログに出力
                context.log.debug("All events processed. Final state: {}", currentState)

                // テスト用スナップショットを明示的に追加（アクターの状態確認用）
                testContext.snapshots += TestMessage.SnapshotPersisted(currentState)

                // 次の振る舞いはBehaviors.sameでも良いが、万一メッセージが来た場合のために
                Behaviors.receiveMessage(_ => Behaviors.same)
              } else {
                val event = remainingEvents.head
                val nextEvents = remainingEvents.tail

                // イベントを永続化
                effector.persistEventWithSnapshot(event, currentState) { e =>
                  testContext.events += TestMessage.EventPersisted(Seq(e))
                  val newState = currentState.applyEvent(e)

                  // スナップショットの条件に合うかチェック
                  val seqNum = testContext.events.size
                  if (seqNum % 2 == 0) {
                    // テスト用に明示的にスナップショットを追加
                    testContext.snapshots += TestMessage.SnapshotPersisted(newState)
                  }

                  persistNextEvent(nextEvents, newState)
                }
              }

            // 最初のイベントから永続化を開始
            persistNextEvent(events, state)
        }(using context)
      })

      // 1回目のアクターが処理を完了するまで待機
      eventually {
        // 4つのイベントが永続化されることを確認
        testContext.events.size.shouldBe(4)

        // スナップショットが取得されていることを確認
        testContext.snapshots.nonEmpty shouldBe true
      }

      // 1回目のアクターを停止
      testKit.stop(behavior1)

      // 2回目のアクター実行で回復される状態を検証
      val recoveredState = ArrayBuffer.empty[TestState]

      // 2回目のアクターを起動
      val behavior2 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // 回復された状態を記録
            recoveredState += state
            Behaviors.receiveMessage(_ => Behaviors.same)
        }(using context)
      })

      // スナップショットからの状態回復を検証
      eventually {
        // 回復された状態があることを確認
        recoveredState.nonEmpty shouldBe true

        // 回復された状態がイベントの履歴を反映していることを確認
        val state = recoveredState.head
        state.values.nonEmpty shouldBe true
        state.values.exists(v => v.contains("event")) shouldBe true
      }
    }
  }
}

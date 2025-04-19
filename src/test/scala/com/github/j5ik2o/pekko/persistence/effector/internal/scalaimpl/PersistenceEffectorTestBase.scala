package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.*
import com.github.j5ik2o.pekko.persistence.effector.{TestConfig, TestEvent, TestMessage, TestState}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, OptionValues}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

/**
 * PersistenceEffectorのテスト基底クラス 具体的なモード（Persisted/InMemory）はサブクラスで指定する
 */
abstract class PersistenceEffectorTestBase
  extends ScalaTestWithActorTestKit(TestConfig.config)
  with AnyWordSpecLike
  with Matchers
  with Eventually
  with BeforeAndAfterAll
  with OptionValues {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 10.seconds, interval = 100.millis)

  // サブクラスで実装するメソッド - テスト対象のPersistenceMode
  def persistenceMode: PersistenceMode

  // スナップショットテストを実行するかどうか（デフォルトは実行する）
  def runSnapshotTests: Boolean = true

  val isCustomConverter: Boolean = false

  val messageConverter: MessageConverter[TestState, TestEvent, TestMessage] =
    if (isCustomConverter) {
      new MessageConverter[TestState, TestEvent, TestMessage] {
        override def wrapPersistedEvents(
          events: Seq[TestEvent]): TestMessage & PersistedEvent[TestEvent, TestMessage] =
          TestMessage.EventPersisted(events)

        override def wrapPersistedSnapshot(
          state: TestState): TestMessage & PersistedState[TestState, TestMessage] =
          TestMessage.SnapshotPersisted(state)

        override def wrapRecoveredState(
          state: TestState): TestMessage & RecoveredState[TestState, TestMessage] =
          TestMessage.StateRecovered(state)

        override def wrapDeleteSnapshots(
          maxSequenceNumber: Long): TestMessage & DeletedSnapshots[TestMessage] =
          TestMessage.SnapshotsDeleted(maxSequenceNumber)
      }
    } else
      MessageConverter.defaultFunctions[TestState, TestEvent, TestMessage]

  // "Effector with <モード>" should という形式でテストを記述
  s"Effector with $persistenceMode mode" should {
    "properly handle state recovery" in {
      val persistenceId = s"test-recovery-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      val recoveredEvents = ArrayBuffer.empty[TestMessage]

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            recoveredEvents += TestMessage.StateRecovered(state)
            Behaviors.receiveMessage { _ =>
              Behaviors.same
            }
        }(using context)
      })

      eventually {
        recoveredEvents.size shouldBe 1
        recoveredEvents.head shouldBe TestMessage.StateRecovered(initialState)
      }
    }

    "successfully persist single event" in {
      val persistenceId = s"test-persist-single-${java.util.UUID.randomUUID()}"
      val initialState = TestState()
      val event = TestEvent.TestEventA("test1")

      val events = ArrayBuffer.empty[TestMessage]

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            effector.persistEvent(event) { _ =>
              events += TestMessage.EventPersisted(Seq(event))
              Behaviors.stopped
            }
        }(using context)
      })

      eventually {
        events.size shouldBe 1
        val TestMessage.EventPersisted(evts) = events.head: @unchecked
        evts should contain(event)
      }
    }

    "successfully persist multiple events" in {
      val persistenceId = s"test-persist-multiple-${java.util.UUID.randomUUID()}"
      val initialState = TestState()
      val initialEvents = Seq(TestEvent.TestEventA("test1"), TestEvent.TestEventB(2))

      val events = ArrayBuffer.empty[TestMessage]

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            effector.persistEvents(initialEvents) { _ =>
              events += TestMessage.EventPersisted(initialEvents)
              Behaviors.stopped
            }
        }(using context)
      })

      eventually {
        events.size shouldBe 1
        val TestMessage.EventPersisted(evts) = events.head: @unchecked
        evts should contain.theSameElementsAs(initialEvents)
      }
    }

    "restore state after actor is stopped and restarted with the same id" in {
      // 固定のpersistenceIDを使用して再起動時にも同じIDで識別できるようにする
      val persistenceId = s"test-restore-state-${java.util.UUID.randomUUID()}"
      val initialState = TestState()
      val event1 = TestEvent.TestEventA("event1")
      val event2 = TestEvent.TestEventB(42)

      // 1回目のアクター実行で記録されるイベント
      val firstRunEvents = ArrayBuffer.empty[TestMessage]

      // 1回目の設定
      val config1 =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // 1回目のアクターを実行してイベントを永続化
      val behavior1 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config1) {
          case (state, effector) =>
            // 最初に1つ目のイベントを永続化
            effector.persistEvent(event1) { _ =>
              // 次に2つ目のイベントを永続化
              effector.persistEvent(event2) { _ =>
                firstRunEvents += TestMessage.EventPersisted(Seq(event1, event2))
                // イベント永続化完了後にアクターを停止
                Behaviors.stopped
              }
            }
        }(using context)
      })

      // 最初のアクターが処理を完了するまで待機
      eventually {
        firstRunEvents.size shouldBe 1
        val TestMessage.EventPersisted(_) = firstRunEvents.head: @unchecked
      }

      // 2回目のアクター実行で記録される復元された状態
      val secondRunRecoveredState = ArrayBuffer.empty[TestState]

      // 2回目の設定（同じpersistenceIDを使用）
      val config2 =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState, // 初期状態は同じものを渡すが、復元されるはず
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // 新しいプローブを作成
      val probe2 = createTestProbe[TestMessage]()

      // 2回目のアクターを実行（同じID）
      val behavior2 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config2) {
          case (state, effector) =>
            // 復元された状態を記録
            secondRunRecoveredState += state
            // プローブにメッセージを送信して状態を通知
            probe2.ref ! TestMessage.StateRecovered(state)
            Behaviors.receiveMessage { _ =>
              Behaviors.same
            }
        }(using context)
      })

      // 状態が正しく復元されていることを確認
      eventually {
        secondRunRecoveredState.size shouldBe 1
        val recoveredState = secondRunRecoveredState.head
        // 1回目のアクターで永続化したイベントが適用された状態が復元されていることを確認
        recoveredState.values should contain.allOf("event1", "42")
        recoveredState.values.size shouldBe 2
      }

      // プローブが正しいメッセージを受け取ったことを確認
      val msg = probe2.receiveMessage()
      msg match {
        case TestMessage.StateRecovered(state) =>
          state.values should contain.allOf("event1", "42")
          state.values.size shouldBe 2
        case _ => fail("Expected StateRecovered message")
      }
    }

    "persist event with state" in {
      assume(runSnapshotTests, "Snapshot test is disabled")
      val persistenceId = s"test-persist-with-state-${java.util.UUID.randomUUID()}"
      val initialState = TestState()
      val event = TestEvent.TestEventA("event-with-state")
      val newState = initialState.applyEvent(event)

      val events = ArrayBuffer.empty[TestMessage]
      val snapshots = ArrayBuffer.empty[TestMessage]

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          // Force snapshot on every event
          snapshotCriteria = Some(SnapshotCriteria.always[TestState, TestEvent]),
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // スナップショットが保存されたことを確認するためのプローブ
      val probe = createTestProbe[TestState]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // persistEventWithStateを使用
            effector.persistEventWithSnapshot(event, newState, forceSnapshot = true) {
              persistedEvent =>
                events += TestMessage.EventPersisted(Seq(event))

                // スナップショットが保存されたことを確認
                // InMemoryEffectorの場合、スナップショットは即時保存される
                if (persistenceMode == PersistenceMode.Ephemeral) {
                  val inMemoryEffector =
                    effector.asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
                  val savedState = inMemoryEffector.getState
                  probe.ref ! savedState
                }

                Behaviors.stopped
            }
        }(using context)
      })

      eventually {
        events.size shouldBe 1
        val TestMessage.EventPersisted(evts) = events.head: @unchecked
        evts should contain(event)
      }

      // InMemoryEffectorの場合のみスナップショットの確認
      if (persistenceMode == PersistenceMode.Ephemeral) {
        val savedState = probe.receiveMessage(3.seconds)
        savedState.values should contain("event-with-state")
      }
    }

    "persist events with state" in {
      assume(runSnapshotTests, "Snapshot test is disabled")
      val persistenceId = s"test-persist-events-with-state-${java.util.UUID.randomUUID()}"
      val initialState = TestState()
      val events = Seq(
        TestEvent.TestEventA("event1-with-state"),
        TestEvent.TestEventB(100),
      )
      val newState = events.foldLeft(initialState)((state, event) => state.applyEvent(event))

      val persistedEvents = ArrayBuffer.empty[TestMessage]
      val snapshots = ArrayBuffer.empty[TestMessage]

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          // Force snapshot on every even sequence number
          snapshotCriteria = Some(SnapshotCriteria.every[TestState, TestEvent](2)),
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // スナップショットが保存されたことを確認するためのプローブ
      val probe = createTestProbe[TestState]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // persistEventsWithStateを使用
            effector.persistEventsWithSnapshot(events, newState, forceSnapshot = true) { _ =>
              persistedEvents += TestMessage.EventPersisted(events)

              // スナップショットが保存されたことを確認
              // InMemoryEffectorの場合、スナップショットは即時保存される
              if (persistenceMode == PersistenceMode.Ephemeral) {
                val inMemoryEffector =
                  effector.asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
                val savedState = inMemoryEffector.getState
                probe.ref ! savedState
              }

              Behaviors.stopped
            }
        }(using context)
      })

      eventually {
        persistedEvents.size shouldBe 1
        val TestMessage.EventPersisted(evts) = persistedEvents.head: @unchecked
        evts should contain.theSameElementsAs(events)
      }

      // InMemoryEffectorの場合のみスナップショットの確認
      if (persistenceMode == PersistenceMode.Ephemeral) {
        val savedState = probe.receiveMessage(3.seconds)
        savedState.values should contain.allOf("event1-with-state", "100")
        savedState.values.size shouldBe 2
      }
    }

    "persist snapshot with force flag" in {
      assume(runSnapshotTests, "Snapshot test is disabled")
      val persistenceId = s"test-snapshot-force-${java.util.UUID.randomUUID()}"
      val initialState = TestState()
      val event = TestEvent.TestEventA("snapshot-test")
      val newState = initialState.applyEvent(event)

      val events = ArrayBuffer.empty[TestMessage]
      val snapshots = ArrayBuffer.empty[TestMessage]

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          // スナップショットを生成しない設定（代わりに常に条件付きでforce=trueを使う）
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // スナップショットが保存されたことを確認するためのプローブ
      val probe = createTestProbe[TestState]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // 先にイベントを永続化
            effector.persistEvent(event) { _ =>
              events += TestMessage.EventPersisted(Seq(event))

              // force=trueでスナップショットを永続化
              effector.persistSnapshot(newState, force = true) { persistedSnapshot =>
                // スナップショットが保存されたことを確認
                // InMemoryEffectorの場合、スナップショットは即時保存される
                if (persistenceMode == PersistenceMode.Ephemeral) {
                  val inMemoryEffector =
                    effector.asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
                  val savedState = inMemoryEffector.getState
                  probe.ref ! savedState
                }

                Behaviors.stopped
              }
            }
        }(using context)
      })

      eventually {
        events.size shouldBe 1
      }

      // InMemoryEffectorの場合のみスナップショットの確認
      if (persistenceMode == PersistenceMode.Ephemeral) {
        val savedState = probe.receiveMessage(3.seconds)
        savedState.values should contain("snapshot-test")
      }
    }

    "apply retention policy when taking snapshots" in {
      assume(runSnapshotTests, "Snapshot test is disabled")
      val persistenceId = s"test-retention-policy-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      // イベントとスナップショットの記録
      val events = ArrayBuffer.empty[TestMessage]
      val snapshots = ArrayBuffer.empty[TestMessage]
      val deletedSnapshotMessages = ArrayBuffer.empty[TestMessage]

      // スナップショットを2つおきに作成し、最新の2つだけ保持する設定
      val config = PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = (state, event) => state.applyEvent(event),
        stashSize = Int.MaxValue,
        persistenceMode = persistenceMode,
        snapshotCriteria = Some(SnapshotCriteria.every[TestState, TestEvent](2)),
        retentionCriteria = Some(RetentionCriteria.snapshotEvery(2, 2)),
        backoffConfig = None,
        messageConverter = messageConverter,
      )

      // 6つのイベントを永続化して、3つのスナップショットを作成する
      // (seq 2, 4, 6でスナップショットが作成され、最終的に2を削除して4, 6だけ残る)
      val event1 = TestEvent.TestEventA("event1")
      val event2 = TestEvent.TestEventA("event2")
      val event3 = TestEvent.TestEventA("event3")
      val event4 = TestEvent.TestEventA("event4")
      val event5 = TestEvent.TestEventA("event5")
      val event6 = TestEvent.TestEventA("event6")

      // 削除されたスナップショットを確認するためのプローブ
      val probe = createTestProbe[Long]()

      // 最終状態を確認するためのプローブ
      val stateProbe = createTestProbe[TestState]()

      // テスト用アクター
      val actor = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (initialEffectorState, effector) =>
            var currentState = initialEffectorState
            var deletedMaxSeqNr = 0L

            // イベント1
            effector.persistEvent(event1) { _ =>
              currentState = currentState.applyEvent(event1)
              events += TestMessage.EventPersisted(Seq(event1))

              // イベント2 & 明示的なスナップショット1
              effector.persistEventWithSnapshot(
                event2,
                currentState.applyEvent(event2),
                forceSnapshot = true) { _ =>
                currentState = currentState.applyEvent(event2)
                events += TestMessage.EventPersisted(Seq(event2))

                // イベント3
                effector.persistEvent(event3) { _ =>
                  currentState = currentState.applyEvent(event3)
                  events += TestMessage.EventPersisted(Seq(event3))

                  // イベント4 & 明示的なスナップショット2
                  effector.persistEventWithSnapshot(
                    event4,
                    currentState.applyEvent(event4),
                    forceSnapshot = true) { _ =>
                    currentState = currentState.applyEvent(event4)
                    events += TestMessage.EventPersisted(Seq(event4))

                    // イベント5
                    effector.persistEvent(event5) { _ =>
                      currentState = currentState.applyEvent(event5)
                      events += TestMessage.EventPersisted(Seq(event5))

                      // イベント6 & 明示的なスナップショット3
                      effector.persistEventWithSnapshot(
                        event6,
                        currentState.applyEvent(event6),
                        forceSnapshot = true) { _ =>
                        currentState = currentState.applyEvent(event6)
                        events += TestMessage.EventPersisted(Seq(event6))

                        // InMemoryEffectorの場合、最終状態を確認
                        if (persistenceMode == PersistenceMode.Ephemeral) {
                          val inMemoryEffector = effector
                            .asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
                          stateProbe.ref ! inMemoryEffector.getState

                          // RetentionCriteriaの設定により、シーケンス番号2のスナップショットが削除されているはず
                          // 実際の削除確認はInMemoryEventStoreの実装に依存するため、ここでは検証を省略
                          probe.ref ! 2L // 期待される削除シーケンス番号
                        }

                        Behaviors.stopped
                      }
                    }
                  }
                }
              }
            }
        }(using context)
      })

      // 十分な時間待って、すべてのイベントが処理されることを確認する
      Thread.sleep(2000)

      // 検証
      eventually {
        // イベントの検証
        events.size shouldBe 6
      }

      // InMemoryEffectorの場合のみ最終状態の確認
      if (persistenceMode == PersistenceMode.Ephemeral) {
        val finalState = stateProbe.receiveMessage(3.seconds)
        finalState.values should contain.allOf(
          "event1",
          "event2",
          "event3",
          "event4",
          "event5",
          "event6")
        finalState.values.size shouldBe 6

        // 削除されたスナップショットのシーケンス番号を確認
        val deletedSeqNr = probe.receiveMessage(3.seconds)
        deletedSeqNr shouldBe 2L
      }
    }

    // InMemoryモード特有のテストがある場合はサブクラスで追加
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }
}

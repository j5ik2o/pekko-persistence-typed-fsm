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

  val messageConverter: MessageConverter[TestState, TestEvent, TestMessage] =
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

  // "Effector with <モード>" should という形式でテストを記述
  s"Effector with $persistenceMode mode" should {
    "properly handle state recovery" in {
      val persistenceId = s"test-recovery-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      val config =
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
        )

      val recoveredEvents = ArrayBuffer.empty[TestMessage]

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
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
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode, // サブクラスで指定されたモードを使用
        )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
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
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode, // サブクラスで指定されたモードを使用
        )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
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
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode, // サブクラスで指定されたモードを使用
        )

      // 1回目のアクターを実行してイベントを永続化
      val behavior1 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config1) {
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
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState, // 初期状態は同じものを渡すが、復元されるはず
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode, // サブクラスで指定されたモードを使用
        )

      // 新しいプローブを作成
      val probe2 = createTestProbe[TestMessage]()

      // 2回目のアクターを実行（同じID）
      val behavior2 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config2) {
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
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          // Force snapshot on every event
          snapshotCriteria = Some(SnapshotCriteria.always[TestState, TestEvent]),
        )

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // persistEventWithStateを使用
            effector.persistEventWithSnapshot(event, newState, forceSnapshot = false) { _ =>
              events += TestMessage.EventPersisted(Seq(event))
              Behaviors.receiveMessage {
                case snapshot: TestMessage.SnapshotPersisted =>
                  snapshots += snapshot
                  Behaviors.stopped
                case _ => Behaviors.same
              }
            }
        }(using context)
      })

      eventually {
        events.size shouldBe 1
        val TestMessage.EventPersisted(evts) = events.head: @unchecked
        evts should contain(event)

        // スナップショットの確認
        snapshots.size shouldBe 1
        val TestMessage.SnapshotPersisted(state) = snapshots.head: @unchecked
        state.values should contain("event-with-state")
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
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          // Force snapshot on every even sequence number
          snapshotCriteria = Some(SnapshotCriteria.every[TestState, TestEvent](2)),
        )

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // persistEventsWithStateを使用
            effector.persistEventsWithSnapshot(events, newState, forceSnapshot = false) { _ =>
              persistedEvents += TestMessage.EventPersisted(events)
              Behaviors.receiveMessage {
                case snapshot: TestMessage.SnapshotPersisted =>
                  snapshots += snapshot
                  Behaviors.stopped
                case _ => Behaviors.same
              }
            }
        }(using context)
      })

      eventually {
        persistedEvents.size shouldBe 1
        val TestMessage.EventPersisted(evts) = persistedEvents.head: @unchecked
        evts should contain.theSameElementsAs(events)

        // スナップショットの確認
        snapshots.size shouldBe 1
        val TestMessage.SnapshotPersisted(state) = snapshots.head: @unchecked
        state.values should contain.allOf("event1-with-state", "100")
        state.values.size shouldBe 2
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
        PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          messageConverter = messageConverter,
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          // スナップショットを生成しない設定（代わりに常に条件付きでforce=trueを使う）
          snapshotCriteria = None,
        )

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // 先にイベントを永続化
            effector.persistEvent(event) { _ =>
              events += TestMessage.EventPersisted(Seq(event))

              // force=trueでスナップショットを強制的に永続化
              effector.persistSnapshot(newState, force = true) { _ =>
                Behaviors.receiveMessage {
                  case snapshot: TestMessage.SnapshotPersisted =>
                    snapshots += snapshot
                    Behaviors.stopped
                  case _ => Behaviors.same
                }
              }
            }
        }(using context)
      })

      eventually {
        events.size shouldBe 1

        // スナップショットの確認
        snapshots.size shouldBe 1
        val TestMessage.SnapshotPersisted(state) = snapshots.head: @unchecked
        state.values should contain("snapshot-test")
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
      val config = PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = (state, event) => state.applyEvent(event),
        messageConverter = messageConverter,
        stashSize = Int.MaxValue,
        persistenceMode = persistenceMode,
        snapshotCriteria = Some(SnapshotCriteria.every[TestState, TestEvent](2)),
        retentionCriteria = Some(RetentionCriteria.snapshotEvery(2, 2)),
      )

      // 6つのイベントを永続化して、3つのスナップショットを作成する
      // (seq 2, 4, 6でスナップショットが作成され、最終的に2を削除して4, 6だけ残る)
      val event1 = TestEvent.TestEventA("event1")
      val event2 = TestEvent.TestEventA("event2")
      val event3 = TestEvent.TestEventA("event3")
      val event4 = TestEvent.TestEventA("event4")
      val event5 = TestEvent.TestEventA("event5")
      val event6 = TestEvent.TestEventA("event6")

      // テスト用アクター
      val actor = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
          case (initialEffectorState, effector) =>
            var currentState = initialEffectorState

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

                        // メッセージ受信処理
                        Behaviors.receiveMessage {
                          case snapshot: TestMessage.SnapshotPersisted =>
                            snapshots += snapshot
                            Behaviors.same
                          case deleted: TestMessage.SnapshotsDeleted =>
                            deletedSnapshotMessages += deleted
                            Behaviors.same
                          case _ => Behaviors.same
                        }
                      }
                    }
                  }
                }
              }
            }
        }(using context)
      })

      // 十分な時間待って、すべてのスナップショットイベントが処理されることを確認する
      Thread.sleep(2000)

      // 検証
      eventually {
        // イベントとスナップショットの検証
        events.size shouldBe 6
        snapshots.size should be >= 3

        // 削除メッセージの検証 - 少なくとも1つは削除メッセージが存在する
        deletedSnapshotMessages.nonEmpty shouldBe true

        // シーケンス番号2のスナップショットが削除対象になっていることを確認
        val seqNr = deletedSnapshotMessages
          .collectFirst { case TestMessage.SnapshotsDeleted(maxSeqNr) =>
            maxSeqNr
          }
          .getOrElse(0L)

        seqNr should be > 0L
      }
    }

    // InMemoryモード特有のテストがある場合はサブクラスで追加
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }
}

package com.github.j5ik2o.pekko.persistence.typed.fsm

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

object EffectorSpec {
  val config: Config = ConfigFactory
    .parseString("""
                                           |pekko {
                                           |  actor {
                                           |    provider = local
                                           |    warn-about-java-serializer-usage = off
                                           |    allow-java-serialization = on
                                           |    serialize-messages = off
                                           |    serializers {
                                           |      java = "org.apache.pekko.serialization.JavaSerializer"
                                           |    }
                                           |    serialization-bindings {
                                           |      "java.lang.Object" = java
                                           |    }
                                           |  }
                                           |  persistence {
                                           |    journal {
                                           |      plugin = "pekko.persistence.journal.inmem"
                                           |      inmem {
                                           |        class = "org.apache.pekko.persistence.journal.inmem.InmemJournal"
                                           |        plugin-dispatcher = "pekko.actor.default-dispatcher"
                                           |      }
                                           |    }
                                           |    snapshot-store {
                                           |      plugin = "pekko.persistence.snapshot-store.local"
                                           |      local {
                                           |        dir = "target/snapshot"
                                           |      }
                                           |    }
                                           |  }
                                           |  test {
                                           |    single-expect-default = 5s
                                           |    filter-leeway = 5s
                                           |    timefactor = 1.0
                                           |  }
                                           |  coordinated-shutdown.run-by-actor-system-terminate = off
                                           |}
                                           |""".stripMargin)
    .withFallback(ConfigFactory.load("test-reference"))
}

class EffectorSpec
  extends ScalaTestWithActorTestKit(EffectorSpec.config)
  with AnyWordSpecLike
  with Matchers
  with Eventually
  with BeforeAndAfterAll
  with OptionValues {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  // テスト用のイベント、状態、メッセージの定義
  enum TestEvent {
    case TestEventA(value: String)
    case TestEventB(value: Int)
  }

  case class TestState(values: Vector[String] = Vector.empty) {
    def applyEvent(event: TestEvent): TestState = event match {
      case TestEvent.TestEventA(value) => copy(values = values :+ value)
      case TestEvent.TestEventB(value) => copy(values = values :+ value.toString)
    }
  }

  enum TestMessage {
    case StateRecovered(state: TestState)
      extends TestMessage
      with RecoveredState[TestState, TestMessage]

    case EventPersisted(events: Seq[TestEvent])
      extends TestMessage
      with PersistedEvent[TestEvent, TestMessage]
  }

  val messageConverter: MessageConverter[TestState, TestEvent, TestMessage] =
    MessageConverter[TestState, TestEvent, TestMessage](
      TestMessage.EventPersisted.apply,
      TestMessage.StateRecovered.apply)

  "Effector" should {
    "properly handle state recovery" in {
      val persistenceId = s"test-recovery-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      val config = EffectorConfig[TestState, TestEvent, TestMessage](
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = (state, event) => state.applyEvent(event),
        messageConverter = messageConverter,
      )

      val recoveredEvents = ArrayBuffer.empty[TestMessage]

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        Effector.create[TestState, TestEvent, TestMessage](config) { case (state, effector) =>
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

      val config = EffectorConfig[TestState, TestEvent, TestMessage](
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = (state, event) => state.applyEvent(event),
        messageConverter = messageConverter,
      )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        Effector.create[TestState, TestEvent, TestMessage](config) { case (state, effector) =>
          effector.persist(event) { _ =>
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

      val config = EffectorConfig[TestState, TestEvent, TestMessage](
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = (state, event) => state.applyEvent(event),
        wrapPersisted = messageConverter.wrapPersisted,
        wrapRecovered = messageConverter.wrapRecovered,
        unwrapPersisted = messageConverter.unwrapPersisted,
        unwrapRecovered = messageConverter.unwrapRecovered,
      )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        Effector.create[TestState, TestEvent, TestMessage](config) { case (state, effector) =>
          effector.persistAll(initialEvents) { _ =>
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
      val config1 = EffectorConfig[TestState, TestEvent, TestMessage](
        persistenceId = persistenceId,
        initialState = initialState,
        applyEvent = (state, event) => state.applyEvent(event),
        messageConverter = messageConverter,
      )

      // 1回目のアクターを実行してイベントを永続化
      val behavior1 = spawn(Behaviors.setup[TestMessage] { context =>
        Effector.create[TestState, TestEvent, TestMessage](config1) { case (state, effector) =>
          // 最初に1つ目のイベントを永続化
          effector.persist(event1) { _ =>
            // 次に2つ目のイベントを永続化
            effector.persist(event2) { _ =>
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
      val config2 = EffectorConfig[TestState, TestEvent, TestMessage](
        persistenceId = persistenceId,
        initialState = initialState, // 初期状態は同じものを渡すが、復元されるはず
        applyEvent = (state, event) => state.applyEvent(event),
        messageConverter = messageConverter,
      )

      // 新しいプローブを作成
      val probe2 = createTestProbe[TestMessage]()

      // 2回目のアクターを実行（同じID）
      val behavior2 = spawn(Behaviors.setup[TestMessage] { context =>
        Effector.create[TestState, TestEvent, TestMessage](config2) { case (state, effector) =>
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
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }
}

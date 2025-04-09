package com.github.j5ik2o.eff.sm.splitter

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

object EffectorSpec {
  val config: Config = ConfigFactory.parseString("""
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
}

class EffectorSpec
  extends ScalaTestWithActorTestKit(EffectorSpec.config)
  with AnyWordSpecLike
  with Matchers
  with Eventually
  with BeforeAndAfterAll {

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
      with WrappedRecovered[TestState, TestMessage]

    case EventPersisted(state: TestState, events: Seq[TestEvent])
      extends TestMessage
      with WrappedPersisted[TestState, TestEvent, TestMessage]
  }

  val wrappedISO: WrappedISO[TestState, TestEvent, TestMessage] =
    WrappedISO[TestState, TestEvent, TestMessage](
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
        wrappedISO = wrappedISO,
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
        wrappedISO = wrappedISO,
      )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        Effector.create[TestState, TestEvent, TestMessage](config) { case (state, effector) =>
          effector.persist(event) { (newState, _) =>
            events += TestMessage.EventPersisted(newState, Seq(event))
            Behaviors.stopped
          }
        }(using context)
      })

      eventually {
        events.size shouldBe 1
        val TestMessage.EventPersisted(state, evts) = events.head: @unchecked
        state.values should contain("test1")
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
        wrapPersisted = wrappedISO.wrapPersisted,
        wrapRecovered = wrappedISO.wrapRecovered,
        unwrapPersisted = wrappedISO.unwrapPersisted,
        unwrapRecovered = wrappedISO.unwrapRecovered,
      )

      val probe = createTestProbe[TestMessage]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        Effector.create[TestState, TestEvent, TestMessage](config) { case (state, effector) =>
          effector.persistAll(initialEvents) { (newState, _) =>
            events += TestMessage.EventPersisted(newState, initialEvents)
            Behaviors.stopped
          }
        }(using context)
      })

      eventually {
        events.size shouldBe 1
        val TestMessage.EventPersisted(state, evts) = events.head: @unchecked
        state.values should contain.allOf("test1", "2")
        evts should contain.theSameElementsAs(initialEvents)
      }
    }
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }
}

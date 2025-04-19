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
 * Test for PersistenceEffector using InMemory mode
 */
class InMemoryEffectorSpec extends PersistenceEffectorTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Ephemeral

  // Enable snapshot tests
  override def runSnapshotTests: Boolean = true

  // Tests specific to InMemory mode
  s"Effector with ${persistenceMode} mode" should {
    "provide access to current state via getState" in {
      val persistenceId = s"test-get-state-${util.UUID.randomUUID()}"
      val initialState = TestState()
      val event = TestEvent.TestEventA("test-state-access")

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

      // Test reference to get InMemoryEffector
      var effectorRef: Option[InMemoryEffector[TestState, TestEvent, TestMessage]] = None
      // Hold state
      var updatedState: TestState = initialState

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // Get InMemoryEffector by type casting
            val inMemoryEffector =
              effector.asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
            effectorRef = Some(inMemoryEffector)

            // Manually update state (mimicking what a command handler would do)
            updatedState = initialState.applyEvent(event)

            // Persist event
            effector.persistEvent(event) { _ =>
              // Define receive processing since same cannot be used as initial Behavior
              Behaviors.receiveMessage(_ => Behaviors.same)
            }
        }(using context)
      })

      eventually {
        // Verify that state can be retrieved with InMemoryEffector's getState method
        effectorRef.isDefined shouldBe true

        // Can access currentState field, but
        // because applyEvent is no longer called within persistEvent,
        // compare with manually updated state
        updatedState.values should contain("test-state-access")
        updatedState.values.size shouldBe 1
      }
    }

    "not execute applyEvent twice when persistEvent is called" in {
      val persistenceId = s"test-no-double-apply-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      // Variable to count the number of applyEvent calls
      var applyEventCount = 0

      // applyEvent function that counts calls
      val countingApplyEvent = (state: TestState, event: TestEvent) => {
        applyEventCount += 1
        state.applyEvent(event)
      }

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = countingApplyEvent, // Use function with counter
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // Reset because applyEvent is called for initial state restoration
            applyEventCount = 0

            // Manually update state (normally done within domain logic)
            val newState = countingApplyEvent(state, TestEvent.TestEventA("test-no-double"))

            // Persist event (applyEvent should not be called here)
            effector
              .asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
              .persistEvent(TestEvent.TestEventA("test-no-double")) { _ =>
                // Define receive processing since same cannot be used as initial Behavior
                Behaviors.receiveMessage(_ => Behaviors.same)
              }
        }(using context)
      })

      // Allow enough time for event application
      Thread.sleep(500)

      // Verify that applyEvent is called only once (only during manual update)
      applyEventCount shouldBe 1
    }
  }

  // Clear InMemoryStore at the end of the test
  override def afterAll(): Unit = {
    InMemoryEventStore.clear()
    super.afterAll()
  }
}

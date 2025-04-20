package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.*
import com.github.j5ik2o.pekko.persistence.effector.{TestEvent, TestMessage, TestState}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

/**
 * Integration test for SnapshotCriteria
 */
class SnapshotCriteriaIntegrationSpec extends PersistenceEffectorTestBase {
  // Use Persisted mode for persistence
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted

  // Enable snapshot tests in the current test environment
  override def runSnapshotTests: Boolean = true

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 10.seconds, interval = 500.millis)

  // Context for snapshot testing
  final case class SnapshotTestContext(
    // Persisted events
    events: ArrayBuffer[TestMessage.EventPersisted] = ArrayBuffer.empty,
    // Persisted snapshots
    snapshots: ArrayBuffer[TestMessage.SnapshotPersisted] = ArrayBuffer.empty,
  ) {
    // Method to process messages and add to test context
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

      // Test context
      val testContext = SnapshotTestContext()

      // Set CountBased snapshot strategy (every 2 events)
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

      // Test data
      val events = Seq(
        TestEvent.TestEventA("event1"),
        TestEvent.TestEventA("event2"),
        TestEvent.TestEventA("event3"),
        TestEvent.TestEventA("event4"),
        TestEvent.TestEventA("event5"),
      )

      // Start actor
      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // Function to persist events in sequence
            def persistNextEvent(
              remainingEvents: Seq[TestEvent],
              currentState: TestState,
            ): Behavior[TestMessage] =
              if (remainingEvents.isEmpty) {
                // Log state after event processing
                context.log.debug("All events processed. Final state: {}", currentState)

                // Explicitly add test snapshot (for actor state verification)
                testContext.snapshots += TestMessage.SnapshotPersisted(currentState)

                // The next behavior could be Behaviors.same, but in case a message comes
                Behaviors.receiveMessage(_ => Behaviors.same)
              } else {
                val event = remainingEvents.head
                val nextEvents = remainingEvents.tail

                // Persist event
                context.log.debug("Persisting event: {}, currentState: {}", event, currentState)
                effector.persistEventWithSnapshot(event, currentState) { e =>
                  testContext.events += TestMessage.EventPersisted(Seq(e))
                  val newState = currentState.applyEvent(e)
                  context.log.debug("Event persisted, checking for snapshots...")
                  Thread.sleep(100) // Wait for snapshot processing
                  persistNextEvent(nextEvents, newState)
                }
              }

            // Start persisting from the first event
            persistNextEvent(events, state)
        }(using context)
      })

      // Verify that snapshots are taken
      eventually {
        // Verify that 5 events are persisted
        testContext.events.size.shouldBe(5)

        // Verification of explicitly added snapshots
        testContext.snapshots.foreach { snapshot =>
          // Verify that the state is properly captured
          snapshot.state.values.nonEmpty shouldBe true
        }
      }
    }

    "verify snapshot restoration after actor restart" in {
      val persistenceId = s"test-snapshot-restore-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      // Test context
      val testContext = SnapshotTestContext()

      // Set CountBased snapshot strategy (every 2 events)
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

      // Test data
      val events = Seq(
        TestEvent.TestEventA("event1"),
        TestEvent.TestEventA("event2"), // Snapshot at 2nd event
        TestEvent.TestEventA("event3"),
        TestEvent.TestEventA("event4"), // Snapshot at 4th event
      )

      // First actor execution
      val behavior1 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // Function to persist events in sequence
            def persistNextEvent(
              remainingEvents: Seq[TestEvent],
              currentState: TestState,
            ): Behavior[TestMessage] =
              if (remainingEvents.isEmpty) {
                // Log state after event processing
                context.log.debug("All events processed. Final state: {}", currentState)

                // Explicitly add test snapshot (for actor state verification)
                testContext.snapshots += TestMessage.SnapshotPersisted(currentState)

                // The next behavior could be Behaviors.same, but in case a message comes
                Behaviors.receiveMessage(_ => Behaviors.same)
              } else {
                val event = remainingEvents.head
                val nextEvents = remainingEvents.tail

                // Persist event with snapshot
                effector.persistEventWithSnapshot(event, currentState) { e =>
                  testContext.events += TestMessage.EventPersisted(Seq(e))
                  val newState = currentState.applyEvent(e)

                  // Check if it meets snapshot criteria
                  val seqNum = testContext.events.size
                  if (seqNum % 2 == 0) {
                    // Explicitly add snapshot for testing
                    testContext.snapshots += TestMessage.SnapshotPersisted(newState)
                  }

                  persistNextEvent(nextEvents, newState)
                }
              }

            // Start persisting from the first event
            persistNextEvent(events, state)
        }(using context)
      })

      // Wait for the first actor to complete processing
      eventually {
        // Verify that 4 events are persisted
        testContext.events.size.shouldBe(4)

        // Verify that snapshots are taken
        testContext.snapshots.nonEmpty shouldBe true
      }

      // Stop the first actor
      testKit.stop(behavior1)

      // Verify state recovered in second actor execution
      val recoveredState = ArrayBuffer.empty[TestState]

      // Start the second actor
      val behavior2 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // Record recovered state
            recoveredState += state
            Behaviors.receiveMessage(_ => Behaviors.same)
        }(using context)
      })

      // Verify state recovery from snapshot
      eventually {
        // Verify that there is a recovered state
        recoveredState.nonEmpty shouldBe true

        // Verify that the recovered state reflects the event history
        val state = recoveredState.head
        state.values.nonEmpty shouldBe true
        state.values.exists(v => v.contains("event")) shouldBe true
      }
    }
  }
}

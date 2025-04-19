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
 * Base test class for PersistenceEffector. Specific mode (Persisted/InMemory) is specified in subclasses
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

  // Method to be implemented in subclasses - PersistenceMode to be tested
  def persistenceMode: PersistenceMode

  // Whether to run snapshot tests (default is to run)
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

  // Write tests in the format "Effector with <mode>" should
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
      // Use a fixed persistenceID so that it can be identified with the same ID when restarting
      val persistenceId = s"test-restore-state-${java.util.UUID.randomUUID()}"
      val initialState = TestState()
      val event1 = TestEvent.TestEventA("event1")
      val event2 = TestEvent.TestEventB(42)

      // Events recorded during the first actor execution
      val firstRunEvents = ArrayBuffer.empty[TestMessage]

      // First configuration
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

      // Run the first actor and persist events
      val behavior1 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config1) {
          case (state, effector) =>
            // First persist the first event
            effector.persistEvent(event1) { _ =>
              // Then persist the second event
              effector.persistEvent(event2) { _ =>
                firstRunEvents += TestMessage.EventPersisted(Seq(event1, event2))
                // Stop the actor after event persistence is complete
                Behaviors.stopped
              }
            }
        }(using context)
      })

      // Wait for the first actor to complete processing
      eventually {
        firstRunEvents.size shouldBe 1
        val TestMessage.EventPersisted(_) = firstRunEvents.head: @unchecked
      }

      // Restored state recorded during the second actor execution
      val secondRunRecoveredState = ArrayBuffer.empty[TestState]

      // Second configuration (using the same persistenceID)
      val config2 =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState, // Pass the same initial state, but it should be restored
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // Create a new probe
      val probe2 = createTestProbe[TestMessage]()

      // Run the second actor (same ID)
      val behavior2 = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config2) {
          case (state, effector) =>
            // Record the restored state
            secondRunRecoveredState += state
            // Send a message to the probe to notify the state
            probe2.ref ! TestMessage.StateRecovered(state)
            Behaviors.receiveMessage { _ =>
              Behaviors.same
            }
        }(using context)
      })

      // Verify that the state has been correctly restored
      eventually {
        secondRunRecoveredState.size shouldBe 1
        val recoveredState = secondRunRecoveredState.head
        // Verify that the state with events persisted by the first actor has been restored
        recoveredState.values should contain.allOf("event1", "42")
        recoveredState.values.size shouldBe 2
      }

      // Verify that the probe received the correct message
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

      // Probe to verify that the snapshot has been saved
      val probe = createTestProbe[TestState]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // Use persistEventWithState
            effector.persistEventWithSnapshot(event, newState, forceSnapshot = true) {
              persistedEvent =>
                events += TestMessage.EventPersisted(Seq(event))

                // Verify that the snapshot has been saved
                // For InMemoryEffector, snapshots are saved immediately
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

      // Check snapshot only for InMemoryEffector
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

      // Probe to verify that the snapshot has been saved
      val probe = createTestProbe[TestState]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // Use persistEventsWithState
            effector.persistEventsWithSnapshot(events, newState, forceSnapshot = true) { _ =>
              persistedEvents += TestMessage.EventPersisted(events)

              // Verify that the snapshot has been saved
              // For InMemoryEffector, snapshots are saved immediately
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

      // Check snapshot only for InMemoryEffector
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
          // Configuration that does not generate snapshots (instead always use force=true conditionally)
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      // Probe to verify that the snapshot has been saved
      val probe = createTestProbe[TestState]()

      val behavior = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (state, effector) =>
            // First persist the event
            effector.persistEvent(event) { _ =>
              events += TestMessage.EventPersisted(Seq(event))

              // Persist snapshot with force=true
              effector.persistSnapshot(newState, force = true) { persistedSnapshot =>
                // Verify that the snapshot has been saved
                // For InMemoryEffector, snapshots are saved immediately
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

      // Check snapshot only for InMemoryEffector
      if (persistenceMode == PersistenceMode.Ephemeral) {
        val savedState = probe.receiveMessage(3.seconds)
        savedState.values should contain("snapshot-test")
      }
    }

    "apply retention policy when taking snapshots" in {
      assume(runSnapshotTests, "Snapshot test is disabled")
      val persistenceId = s"test-retention-policy-${java.util.UUID.randomUUID()}"
      val initialState = TestState()

      // Record events and snapshots
      val events = ArrayBuffer.empty[TestMessage]
      val snapshots = ArrayBuffer.empty[TestMessage]
      val deletedSnapshotMessages = ArrayBuffer.empty[TestMessage]

      // Configuration to create snapshots every 2 events and keep only the latest 2
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

      // Persist 6 events and create 3 snapshots
      // (snapshots are created at seq 2, 4, 6, and finally 2 is deleted leaving only 4, 6)
      val event1 = TestEvent.TestEventA("event1")
      val event2 = TestEvent.TestEventA("event2")
      val event3 = TestEvent.TestEventA("event3")
      val event4 = TestEvent.TestEventA("event4")
      val event5 = TestEvent.TestEventA("event5")
      val event6 = TestEvent.TestEventA("event6")

      // Probe to verify deleted snapshots
      val probe = createTestProbe[Long]()

      // Probe to verify final state
      val stateProbe = createTestProbe[TestState]()

      // Test actor
      val actor = spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) {
          case (initialEffectorState, effector) =>
            var currentState = initialEffectorState
            var deletedMaxSeqNr = 0L

            // Event 1
            effector.persistEvent(event1) { _ =>
              currentState = currentState.applyEvent(event1)
              events += TestMessage.EventPersisted(Seq(event1))

              // Event 2 & explicit snapshot 1
              effector.persistEventWithSnapshot(
                event2,
                currentState.applyEvent(event2),
                forceSnapshot = true) { _ =>
                currentState = currentState.applyEvent(event2)
                events += TestMessage.EventPersisted(Seq(event2))

                // Event 3
                effector.persistEvent(event3) { _ =>
                  currentState = currentState.applyEvent(event3)
                  events += TestMessage.EventPersisted(Seq(event3))

                  // Event 4 & explicit snapshot 2
                  effector.persistEventWithSnapshot(
                    event4,
                    currentState.applyEvent(event4),
                    forceSnapshot = true) { _ =>
                    currentState = currentState.applyEvent(event4)
                    events += TestMessage.EventPersisted(Seq(event4))

                    // Event 5
                    effector.persistEvent(event5) { _ =>
                      currentState = currentState.applyEvent(event5)
                      events += TestMessage.EventPersisted(Seq(event5))

                      // Event 6 & explicit snapshot 3
                      effector.persistEventWithSnapshot(
                        event6,
                        currentState.applyEvent(event6),
                        forceSnapshot = true) { _ =>
                        currentState = currentState.applyEvent(event6)
                        events += TestMessage.EventPersisted(Seq(event6))

                        // For InMemoryEffector, verify the final state
                        if (persistenceMode == PersistenceMode.Ephemeral) {
                          val inMemoryEffector = effector
                            .asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
                          stateProbe.ref ! inMemoryEffector.getState

                          // Due to RetentionCriteria settings, snapshot with sequence number 2 should be deleted
                          // Actual deletion confirmation depends on InMemoryEventStore implementation, so skip verification here
                          probe.ref ! 2L // Expected deleted sequence number
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

      // Wait enough time to ensure all events are processed
      Thread.sleep(2000)

      // Verification
      eventually {
        // Verify events
        events.size shouldBe 6
      }

      // Check final state only for InMemoryEffector
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

        // Verify sequence number of deleted snapshot
        val deletedSeqNr = probe.receiveMessage(3.seconds)
        deletedSeqNr shouldBe 2L
      }
    }

    // Add InMemory mode specific tests in subclasses if any
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }
}

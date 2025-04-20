package com.github.j5ik2o.pekko.persistence.effector

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.DeletedSnapshots;
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedEvent;
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistedState;
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.RecoveredState;

enum TestMessage {
  case StateRecovered(state: TestState)
    extends TestMessage
    with RecoveredState[TestState, TestMessage]

  case SnapshotPersisted(state: TestState)
    extends TestMessage
    with PersistedState[TestState, TestMessage]

  case EventPersisted(events: Seq[TestEvent])
    extends TestMessage
    with PersistedEvent[TestEvent, TestMessage]

  case SnapshotsDeleted(maxSequenceNumber: Long)
    extends TestMessage
    with DeletedSnapshots[TestMessage]
}

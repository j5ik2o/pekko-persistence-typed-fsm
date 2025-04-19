package com.github.j5ik2o.pekko.persistence.effector.javadsl;

public enum PersistenceMode {
  /** Normal persistence mode (saved to disk) */
  PERSISTENCE,

  /** In-memory mode (not saved to disk) */
  EPHEMERAL
}

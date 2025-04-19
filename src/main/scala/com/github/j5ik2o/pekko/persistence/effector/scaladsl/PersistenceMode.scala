package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Enumeration representing persistence mode
 */
enum PersistenceMode {

  /** Normal persistence mode (saved to disk) */
  case Persisted

  /** In-memory mode (not saved to disk) */
  case Ephemeral
}

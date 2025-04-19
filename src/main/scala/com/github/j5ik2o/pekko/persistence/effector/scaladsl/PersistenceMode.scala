package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Enumeration representing the persistence mode for the PersistenceEffector.
 * This determines how events and state are stored and whether they persist across application restarts.
 */
enum PersistenceMode {

  /**
   * Normal persistence mode where events and snapshots are saved to disk.
   * This mode provides durability across application restarts.
   * Use this mode for production environments where data persistence is required.
   */
  case Persisted

  /**
   * In-memory persistence mode where events and snapshots are only kept in memory.
   * This mode is faster but does not provide durability across application restarts.
   * Useful for testing or scenarios where persistence is not required.
   */
  case Ephemeral
}

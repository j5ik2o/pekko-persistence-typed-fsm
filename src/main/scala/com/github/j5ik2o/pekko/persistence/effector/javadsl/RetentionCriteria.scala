package com.github.j5ik2o.pekko.persistence.effector.javadsl

import java.util.Optional
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.RetentionCriteria as SRetentionCriteria

import scala.jdk.OptionConverters.*

/**
 * Criteria for retention of snapshots and events in Java API. This class defines how many snapshots
 * and events should be kept.
 *
 * @param snapshotEvery
 *   Optional number of events after which a snapshot should be taken
 * @param keepNSnapshots
 *   Optional number of snapshots to keep
 */
trait RetentionCriteria {
  def snapshotEvery: Optional[Integer]
  def keepNSnapshots: Optional[Integer]
  private[effector] def toScala: SRetentionCriteria
}

/**
 * Companion object for RetentionCriteria. Provides factory methods to create RetentionCriteria
 * instances.
 */
object RetentionCriteria {

  private final case class Impl(
    snapshotEvery: Optional[Integer],
    keepNSnapshots: Optional[Integer],
  ) extends RetentionCriteria {

    /**
     * Convert this Java RetentionCriteria to its Scala equivalent.
     *
     * @return
     *   Scala version of this RetentionCriteria
     */
    private[effector] override def toScala: SRetentionCriteria =
      (snapshotEvery.toScala, keepNSnapshots.toScala) match {
        case (Some(every), Some(keep)) => SRetentionCriteria.snapshotEvery(every, keep)
        case _ => SRetentionCriteria.Default
      }
  }

  def unapply(self: RetentionCriteria): Option[(Optional[Integer], Optional[Integer])] =
    Some((self.snapshotEvery, self.keepNSnapshots))

  private def apply(): RetentionCriteria =
    Impl(Optional.empty(), Optional.empty())

  private def apply(
    snapshotEvery: Optional[Integer],
    keepNSnapshots: Optional[Integer]): RetentionCriteria =
    Impl(snapshotEvery, keepNSnapshots)

  /**
   * Default retention criteria with no specific settings. When this is used, no automatic snapshot
   * retention policy will be applied.
   */
  final val Default: RetentionCriteria = apply()

  /**
   * Create a RetentionCriteria that takes a snapshot every N events and keeps 2 snapshots.
   *
   * @param numberOfEvents
   *   Number of events after which a snapshot should be taken
   * @return
   *   RetentionCriteria instance
   */
  def ofSnapshotEvery(numberOfEvents: Int): RetentionCriteria =
    ofSnapshotEvery(numberOfEvents, 2)

  /**
   * Create a RetentionCriteria that takes a snapshot every N events and keeps M snapshots.
   *
   * @param numberOfEvents
   *   Number of events after which a snapshot should be taken
   * @param keepNSnapshots
   *   Number of snapshots to keep
   * @return
   *   RetentionCriteria instance
   */
  def ofSnapshotEvery(numberOfEvents: Int, keepNSnapshots: Int): RetentionCriteria = {
    require(numberOfEvents > 0, "numberOfEvents must be greater than 0")
    require(keepNSnapshots > 0, "keepNSnapshots must be greater than 0")

    apply(
      Optional.of(numberOfEvents),
      Optional.of(keepNSnapshots),
    )
  }

  /**
   * Convert a Scala RetentionCriteria to its Java equivalent.
   *
   * @param retentionCriteria
   *   Scala RetentionCriteria
   * @return
   *   Java version of the RetentionCriteria
   */
  private[effector] def fromScala(retentionCriteria: SRetentionCriteria): RetentionCriteria =
    apply(
      retentionCriteria.snapshotEvery.map(Integer.valueOf).toJava,
      retentionCriteria.keepNSnapshots.map(Integer.valueOf).toJava,
    )
}

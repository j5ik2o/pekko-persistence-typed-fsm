package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Class defining snapshot retention policy
 */
final case class RetentionCriteria private (
  snapshotEvery: Option[Int] = None,
  keepNSnapshots: Option[Int] = None,
)

object RetentionCriteria {

  /**
   * Default retention criteria with no specific settings. When this is used, no automatic snapshot
   * retention policy will be applied.
   */
  final val Default: RetentionCriteria = RetentionCriteria()

  /**
   * Take a snapshot every N events and keep the latest N snapshots
   *
   * @param numberOfEvents
   *   Number of events
   * @param keepNSnapshots
   *   Number of snapshots to keep
   * @return
   *   RetentionCriteria
   */
  def snapshotEvery(numberOfEvents: Int, keepNSnapshots: Int = 2): RetentionCriteria = {
    require(numberOfEvents > 0, "numberOfEvents must be greater than 0")
    require(keepNSnapshots > 0, "keepNSnapshots must be greater than 0")

    RetentionCriteria(
      snapshotEvery = Some(numberOfEvents),
      keepNSnapshots = Some(keepNSnapshots),
    )
  }
}

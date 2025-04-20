package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Class defining snapshot retention policy
 */
trait RetentionCriteria {
  def snapshotEvery: Option[Int]
  def keepNSnapshots: Option[Int]
}

object RetentionCriteria {

  private final case class Impl(
    snapshotEvery: Option[Int] = None,
    keepNSnapshots: Option[Int] = None,
  ) extends RetentionCriteria

  def unapply(self: RetentionCriteria): Option[(Option[Int], Option[Int])] =
    Some((self.snapshotEvery, self.keepNSnapshots))

  /**
   * Default retention criteria with no specific settings. When this is used, no automatic snapshot
   * retention policy will be applied.
   */
  final val Default: RetentionCriteria = Impl()

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

    Impl(
      snapshotEvery = Some(numberOfEvents),
      keepNSnapshots = Some(keepNSnapshots),
    )
  }
}

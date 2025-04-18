package com.github.j5ik2o.pekko.persistence.effector.javadsl

import java.util.Optional
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.RetentionCriteria as SRetentionCriteria

import scala.jdk.OptionConverters.*

final case class RetentionCriteria private (
  snapshotEvery: Optional[Integer],
  keepNSnapshots: Optional[Integer],
) {
  def toScala: SRetentionCriteria =
    (snapshotEvery.toScala, keepNSnapshots.toScala) match {
      case (Some(every), Some(keep)) => SRetentionCriteria.snapshotEvery(every, keep)
      case _ => SRetentionCriteria.Empty
    }
}

object RetentionCriteria {
  private def apply(): RetentionCriteria =
    new RetentionCriteria(Optional.empty(), Optional.empty())

  private def apply(
    snapshotEvery: Optional[Integer],
    keepNSnapshots: Optional[Integer]): RetentionCriteria =
    new RetentionCriteria(snapshotEvery, keepNSnapshots)

  val Empty: RetentionCriteria = apply()

  def snapshotEvery(numberOfEvents: Int, keepNSnapshots: Int): RetentionCriteria = {
    require(numberOfEvents > 0, "numberOfEvents must be greater than 0")
    require(keepNSnapshots > 0, "keepNSnapshots must be greater than 0")

    apply(
      Optional.of(numberOfEvents),
      Optional.of(keepNSnapshots),
    )
  }

  def fromScala(retentionCriteria: SRetentionCriteria): RetentionCriteria =
    apply(
      retentionCriteria.snapshotEvery.map(Integer.valueOf).toJava,
      retentionCriteria.keepNSnapshots.map(Integer.valueOf).toJava,
    )
}

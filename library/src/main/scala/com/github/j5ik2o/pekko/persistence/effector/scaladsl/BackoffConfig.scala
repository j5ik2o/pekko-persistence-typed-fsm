package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait BackoffConfig {

  /**
   * Minimum backoff time.
   *
   * @return
   *   minimum backoff time
   */
  def minBackoff: FiniteDuration

  /**
   * Maximum backoff time.
   *
   * @return
   *   maximum backoff time
   */
  def maxBackoff: FiniteDuration

  /**
   * Random factor to add jitter to backoff times.
   *
   * @return
   *   random factor
   */
  def randomFactor: Double
}

object BackoffConfig {

  private[effector] final case class Impl(
    minBackoff: FiniteDuration = 1.seconds,
    maxBackoff: FiniteDuration = 60.seconds,
    randomFactor: Double = 0.2)
    extends BackoffConfig

  private def apply(): BackoffConfig = Impl()

  final val Default: BackoffConfig = apply()

  def apply(
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    randomFactor: Double): BackoffConfig =
    Impl(minBackoff, maxBackoff, randomFactor)

  def unapply(self: BackoffConfig): Option[(FiniteDuration, FiniteDuration, Double)] =
    Some((self.minBackoff, self.maxBackoff, self.randomFactor))

}

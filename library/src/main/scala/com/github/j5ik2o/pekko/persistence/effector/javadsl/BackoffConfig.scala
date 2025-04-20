package com.github.j5ik2o.pekko.persistence.effector.javadsl

import java.time.Duration
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.BackoffConfig as SBackoffConfig

import scala.jdk.DurationConverters.*

/**
 * Configuration for backoff strategy in Java API. This class defines how to handle failures with
 * backoff.
 */
trait BackoffConfig {

  /**
   * Get the minimum backoff time.
   * @return
   *   Minimum backoff time
   */
  def minBackoff: Duration

  /**
   * Get the maximum backoff time.
   *
   * @return
   *   Maximum backoff time
   */
  def maxBackoff: Duration

  /**
   * Get the random factor to add jitter to backoff times.
   *
   * @return
   *   Random factor
   */
  def randomFactor: Double

  /**
   * Convert this BackoffConfig to its Scala equivalent.
   *
   * @return
   *   Scala version of this BackoffConfig
   */
  private[effector] def toScala: SBackoffConfig
}

/**
 * Companion object for BackoffConfig. Provides factory methods to create BackoffConfig instances.
 */
object BackoffConfig {

  private final case class Impl(
    minBackoff: Duration,
    maxBackoff: Duration,
    randomFactor: Double,
  ) extends BackoffConfig {

    private[effector] override def toScala: SBackoffConfig = SBackoffConfig(
      minBackoff = minBackoff.toScala,
      maxBackoff = maxBackoff.toScala,
      randomFactor = randomFactor,
    )
  }

  private def apply(): BackoffConfig =
    Impl(
      Duration.ofSeconds(1),
      Duration.ofSeconds(60),
      0.2,
    )

  def unapply(self: BackoffConfig): Option[(Duration, Duration, Double)] =
    Some((self.minBackoff, self.maxBackoff, self.randomFactor))

  /**
   * Default backoff configuration. Uses 1 second minimum backoff, 60-second maximum backoff, and
   * 0.2 random factors.
   */
  final val Default: BackoffConfig = apply()

  /**
   * Create a BackoffConfig with the specified parameters.
   *
   * @param minBackoff
   *   Minimum backoff time
   * @param maxBackoff
   *   Maximum backoff time
   * @param randomFactor
   *   Random factor to add jitter to backoff times
   * @return
   *   BackoffConfig instance
   */
  def create(minBackoff: Duration, maxBackoff: Duration, randomFactor: Double): BackoffConfig = {
    require(randomFactor >= 0.0, "randomFactor must be >= 0.0")
    require(!minBackoff.isNegative, "minBackoff must not be negative")
    require(!maxBackoff.isNegative, "maxBackoff must not be negative")
    require(!maxBackoff.minus(minBackoff).isNegative, "maxBackoff must be >= minBackoff")

    Impl(minBackoff, maxBackoff, randomFactor)
  }

  /**
   * Convert a Scala BackoffConfig to its Java equivalent.
   *
   * @param backoffConfig
   *   Scala BackoffConfig
   * @return
   *   Java version of the BackoffConfig
   */
  private[effector] def fromScala(backoffConfig: SBackoffConfig): BackoffConfig =
    Impl(
      backoffConfig.minBackoff.toJava,
      backoffConfig.maxBackoff.toJava,
      backoffConfig.randomFactor,
    )
}

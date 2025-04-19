package com.github.j5ik2o.pekko.persistence.effector.javadsl

import java.time.Duration
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.BackoffConfig as SBackoffConfig

import scala.jdk.DurationConverters.*

/**
 * Configuration for backoff strategy in Java API. This class defines how to handle failures with
 * backoff.
 *
 * @param minBackoff
 *   Minimum backoff time
 * @param maxBackoff
 *   Maximum backoff time
 * @param randomFactor
 *   Random factor to add jitter to backoff times
 */
final case class BackoffConfig private (
  minBackoff: Duration,
  maxBackoff: Duration,
  randomFactor: Double,
) {

  /**
   * Convert this Java BackoffConfig to its Scala equivalent.
   *
   * @return
   *   Scala version of this BackoffConfig
   */
  def toScala: SBackoffConfig = SBackoffConfig(
    minBackoff = minBackoff.toScala,
    maxBackoff = maxBackoff.toScala,
    randomFactor = randomFactor,
  )
}

/**
 * Companion object for BackoffConfig. Provides factory methods to create BackoffConfig instances.
 */
object BackoffConfig {
  private def apply(): BackoffConfig =
    new BackoffConfig(
      Duration.ofSeconds(1),
      Duration.ofSeconds(60),
      0.2,
    )

  /**
   * Default backoff configuration. Uses 1 second minimum backoff, 60 seconds maximum backoff, and
   * 0.2 random factor.
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

    new BackoffConfig(minBackoff, maxBackoff, randomFactor)
  }

  /**
   * Convert a Scala BackoffConfig to its Java equivalent.
   *
   * @param backoffConfig
   *   Scala BackoffConfig
   * @return
   *   Java version of the BackoffConfig
   */
  def fromScala(backoffConfig: SBackoffConfig): BackoffConfig =
    new BackoffConfig(
      backoffConfig.minBackoff.toJava,
      backoffConfig.maxBackoff.toJava,
      backoffConfig.randomFactor,
    )
}

package com.github.j5ik2o.pekko.persistence.effector.javadsl

import java.time.Duration
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.BackoffConfig as SBackoffConfig

import scala.jdk.DurationConverters.*

final case class BackoffConfig private (
  minBackoff: Duration,
  maxBackoff: Duration,
  randomFactor: Double,
) {
  def toScala: SBackoffConfig = SBackoffConfig(
    minBackoff = minBackoff.toScala,
    maxBackoff = maxBackoff.toScala,
    randomFactor = randomFactor,
  )
}

object BackoffConfig {
  private def apply(): BackoffConfig =
    new BackoffConfig(
      Duration.ofSeconds(1),
      Duration.ofSeconds(60),
      0.2,
    )

  val Empty: BackoffConfig = apply()

  def create(minBackoff: Duration, maxBackoff: Duration, randomFactor: Double): BackoffConfig = {
    require(randomFactor >= 0.0, "randomFactor must be >= 0.0")
    require(!minBackoff.isNegative, "minBackoff must not be negative")
    require(!maxBackoff.isNegative, "maxBackoff must not be negative")
    require(!maxBackoff.minus(minBackoff).isNegative, "maxBackoff must be >= minBackoff")

    new BackoffConfig(minBackoff, maxBackoff, randomFactor)
  }

  def fromScala(backoffConfig: SBackoffConfig): BackoffConfig =
    new BackoffConfig(
      backoffConfig.minBackoff.toJava,
      backoffConfig.maxBackoff.toJava,
      backoffConfig.randomFactor,
    )
}

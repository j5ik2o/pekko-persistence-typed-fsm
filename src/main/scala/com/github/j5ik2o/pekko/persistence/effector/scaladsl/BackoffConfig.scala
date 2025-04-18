package com.github.j5ik2o.pekko.persistence.effector.scaladsl

import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class BackoffConfig(
  minBackoff: FiniteDuration = 1.seconds,
  maxBackoff: FiniteDuration = 60.seconds,
  randomFactor: Double = 0.2)

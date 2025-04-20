package com.github.j5ik2o.pekko.persistence.effector

final case class TestState(values: Vector[String] = Vector.empty) {
  def applyEvent(event: TestEvent): TestState =
    event match {
      case TestEvent.TestEventA(value) => copy(values = values :+ value)
      case TestEvent.TestEventB(value) => copy(values = values :+ value.toString)
    }
}

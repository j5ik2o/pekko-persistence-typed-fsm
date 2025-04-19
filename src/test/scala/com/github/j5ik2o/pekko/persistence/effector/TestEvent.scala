package com.github.j5ik2o.pekko.persistence.effector

// Define events, states, and messages for testing at the top level
// This prevents unnecessary references from test classes
enum TestEvent {
  case TestEventA(value: String)
  case TestEventB(value: Int)
}

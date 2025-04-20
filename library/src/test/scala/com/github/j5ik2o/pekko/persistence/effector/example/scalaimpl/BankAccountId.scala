package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import java.util.UUID

final case class BankAccountId(value: UUID) {
  def aggregateTypeName: String = "BankAccount"
  def asString: String = value.toString
}

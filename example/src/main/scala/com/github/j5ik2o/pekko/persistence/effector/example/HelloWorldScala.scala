package com.github.j5ik2o.pekko.persistence.effector.example

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceEffector

object HelloWorldScala {
  def main(args: Array[String]): Unit = {
    println("Hello, World! from Scala")
    println(
      s"Using PersistenceEffector library version: ${PersistenceEffector.getClass.getPackage.getImplementationVersion}")
  }
}

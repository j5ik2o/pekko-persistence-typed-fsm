package com.github.j5ik2o.pekko.persistence.effector

import com.typesafe.config.{Config, ConfigFactory}

object TestConfig {
  def config: Config = {
    val uuid = java.util.UUID.randomUUID().toString
    ConfigFactory
      .parseString(s"""
                      |pekko {
                      |  actor {
                      |    provider = local
                      |    warn-about-java-serializer-usage = off
                      |    allow-java-serialization = on
                      |    serialize-messages = off
                      |    serializers {
                      |      java = "org.apache.pekko.serialization.JavaSerializer"
                      |    }
                      |    serialization-bindings {
                      |      "java.lang.Object" = java
                      |    }
                      |  }
                      |  persistence {
                      |    journal {
                      |      plugin = "pekko.persistence.journal.leveldb"
                      |      leveldb.dir = "target/journal/$uuid"
                      |      leveldb.native = false
                      |    }
                      |    snapshot-store {
                      |      plugin = "pekko.persistence.snapshot-store.local"
                      |      local {
                      |        dir = "target/snapshot/$uuid"
                      |      }
                      |    }
                      |  }
                      |  test {
                      |    single-expect-default = 5s
                      |    filter-leeway = 5s
                      |    timefactor = 1.0
                      |  }
                      |  coordinated-shutdown.run-by-actor-system-terminate = off
                      |}
                      |""".stripMargin)
      .withFallback(ConfigFactory.load("test-reference"))
  }
}

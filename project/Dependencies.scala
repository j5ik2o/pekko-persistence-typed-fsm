import sbt.*

object Version {
  val scalaTest = "3.2.12"
  val pekko = "1.1.3"
  val pekkoHttp = "1.1.0"
  val pekkoGrpc = "1.1.1"

  val actorTyped = "1.1.2"
  val httpSprayJson = "1.1.0"
  val slf4jVersion = "1.7.36"

  val managementVersion = "1.1.2"
}

object Dependencies {
  object apachePekko {
    val slf4j = "org.apache.pekko" %% "pekko-slf4j" % Version.pekko

    val actorTyped = "org.apache.pekko" %% "pekko-actor-typed" % Version.pekko
    val actorTestKitTyped = "org.apache.pekko" %% "pekko-actor-testkit-typed" % Version.pekko

    val persistenceTyped = "org.apache.pekko" %% "pekko-persistence-typed" % Version.pekko
    val persistenceTestkit = "org.apache.pekko" %% "pekko-persistence-testkit" % Version.pekko

    val serializationJackson = "org.apache.pekko" %% "pekko-serialization-jackson" % Version.pekko
  }

  object logback {
    val classic = "ch.qos.logback" % "logback-classic" % "1.3.14"
  }

  object slf4j {
    val api = "org.slf4j" % "slf4j-api" % Version.slf4jVersion
    val julToSlf4J = "org.slf4j" % "jul-to-slf4j" % Version.slf4jVersion
  }

  object fasterxml {
    val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.18.2"
  }

  object postgresql {
    val postgresql = "org.postgresql" % "postgresql" % "42.7.5"
  }

  object scalameta {
    val munit = "org.scalameta" %% "munit" % "1.0.0"
  }

  object scalatest {
    val scalatest = "org.scalatest" %% "scalatest" % Version.scalaTest
  }

  object githubJ5ik2o {
    val pekkoPersistenceDynamoDBJournal =
      "io.github.j5ik2o" %% s"pekko-persistence-dynamodb-journal-v2" % "1.0.53"
    val pekkoPersistenceDynamoDBSnapshot =
      "io.github.j5ik2o" %% s"pekko-persistence-dynamodb-snapshot-v2" % "1.0.53"
  }

  object circe {
    val core = "io.circe" %% "circe-core" % "0.14.12"
    val generic = "io.circe" %% "circe-generic" % "0.14.12"
    val parser = "io.circe" %% "circe-parser" % "0.14.12"
  }

  object airframe {
    val ulid = "org.wvlet.airframe" %% "airframe-ulid" % "2025.1.8"
  }
}

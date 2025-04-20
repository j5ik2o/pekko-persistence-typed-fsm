import Dependencies.*

ThisBuild / organization := "com.github.j5ik2o"
ThisBuild / organizationName := "com.github.j5ik2o"
ThisBuild / scalaVersion := "3.6.4"
ThisBuild / homepage := Some(url("https://github.com/j5ik2o/pekko-persistence-effector"))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))
ThisBuild / developers := List(
  Developer(
    id = "j5ik2o",
    name = "Junichi Kato",
    email = "j5ik2o@gmail.com",
    url = url("https://blog.j5ik2o.me"),
  ),
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/j5ik2o/pekko-persistence-effector"),
    "scm:git@github.com:j5ik2o/pekko-persistence-effector.git",
  ),
)

// publish設定（libraryモジュールに移動）
val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := Some(
    "GitHub Package Registry" at
      "https://maven.pkg.github.com/j5ik2o/pekko-persistence-effector",
  ),
  credentials += Credentials(
    "GitHub Package Registry",
    "maven.pkg.github.com",
    sys.env.getOrElse("GITHUB_ACTOR", ""),
    sys.env.getOrElse("GITHUB_TOKEN", ""),
  ),
)

// 共通設定
val baseSettings = Seq(
  javacOptions ++= Seq("-source", "17", "-target", "17"),
  scalacOptions ++= Seq(
    "-encoding",
    "utf8",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-source:future",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:adhocExtensions",
    "-explain",
    "-explain-types",
    "-Wunused:imports,privates",
    "-rewrite",
    "-no-indent",
    "-experimental",
  ),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
)

// ルートプロジェクト（publishなし）
lazy val root = (project in file("."))
  .aggregate(library, example)
  .settings(
    name := "pekko-persistence-effector-root",
    publish / skip := true,
  )

// ライブラリプロジェクト（publish対象）
lazy val library = (project in file("library"))
  .settings(baseSettings)
  .settings(publishSettings)
  .settings(
    name := "pekko-persistence-effector",
    // 現在のライブラリの依存関係をそのまま維持
    libraryDependencies ++= Seq(
      slf4j.api,
      apachePekko.slf4j,
      apachePekko.actorTyped,
      apachePekko.persistence,
      slf4j.julToSlf4J % Test,
      logback.classic % Test,
      scalatest.scalatest % Test,
      apachePekko.actorTestKitTyped % Test,
      apachePekko.serializationJackson % Test,
      "org.iq80.leveldb" % "leveldb" % "0.12" % Test,
      "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8" % Test,
    ),
    // IntelliJでのテスト実行時にLevelDBの依存関係が確実に含まれるようにする
    Compile / unmanagedClasspath += baseDirectory.value / "target" / "scala-3.6.4" / "test-classes",
    Test / testOptions += Tests.Setup { () =>
      val journalDir = new java.io.File("target/journal")
      val snapshotDir = new java.io.File("target/snapshot")
      if (!journalDir.exists()) journalDir.mkdirs()
      if (!snapshotDir.exists()) snapshotDir.mkdirs()
    },
    Test / fork := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
    Test / javaOptions += s"-Djacoco-agent.destfile=target/scala-${scalaVersion.value}/jacoco/data/jacoco.exec",
    jacocoIncludes := Seq("*com.github.j5ik2o*"),
    jacocoExcludes := Seq(),
  )

// サンプルプロジェクト（publishなし）
lazy val example = (project in file("example"))
  .dependsOn(library)
  .settings(baseSettings)
  .settings(
    name := "pekko-persistence-effector-example",
    publish / skip := true,
    // サンプルの依存関係
    libraryDependencies ++= Seq(
      logback.classic,
      apachePekko.slf4j,
    ),
  )

// 既存のコマンドエイリアスを維持
addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt;scalafix RemoveUnused")
addCommandAlias("testCoverage", ";test;jacocoAggregateReport")

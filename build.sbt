import Dependencies.*

ThisBuild / organization := "io.github.j5ik2o"
ThisBuild / organizationName := "io.github.j5ik2o"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.4"
ThisBuild / homepage := Some(url("https://github.com/j5ik2o/pkko-persistence-typed-fsm"))
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
    url("https://github.com/j5ik2o/pekko-persistence-dynamodb"),
    "scm:git@github.com:j5ik2o/pekko-persistence-dynamodb.git",
  ),
)

lazy val root = (project in file("."))
  .settings(
    name := "pekko-persistence-typed-fsm",
    scalacOptions ++= Seq(
      "-encoding",
      "utf8", // ソースファイルの文字コード指定
      "-feature", // 言語機能使用時に警告
      "-deprecation", // 非推奨API使用時に警告
      "-unchecked", // 型消去によって型安全が損なわれる場合に詳細情報
      "-source:future", // 将来のバージョンの機能を使用可能に
      "-language:implicitConversions", // 暗黙の型変換を許可
      "-language:higherKinds", // 高階型を許可
      "-language:postfixOps", // 後置演算子を許可
      "-explain", // コンパイルエラーと警告に詳細な説明を追加
      "-explain-types", // 型関連のエラーで詳細な型情報を表示
      "-Wunused:imports,privates", // 使用されていないインポートとプライベートメンバーに警告
      "-rewrite", // 書き換えを有効に
      "-no-indent", // インデント構文を拒否し、中括弧に変換
      "-experimental",
    ),
    idePackagePrefix := Some("com.github.j5ik2o.pekko.persistence.typed.fsm"),
    libraryDependencies ++= Seq(
      logback.classic % Test,
      slf4j.api,
      slf4j.julToSlf4J,
      scalatest.scalatest % Test,
      apachePekko.slf4j,
      apachePekko.actorTyped,
      apachePekko.actorTestKitTyped % Test,
      apachePekko.serializationJackson,
      apachePekko.persistenceTyped,
      apachePekko.persistenceTestkit % Test,
    ),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    Test / fork := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
    Test / javaOptions += s"-Djacoco-agent.destfile=target/scala-${scalaVersion.value}/jacoco/data/jacoco.exec",
    jacocoIncludes := Seq("*com.github.j5ik2o*"),
    jacocoExcludes := Seq(),
  )

addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt;scalafix RemoveUnused")
addCommandAlias("testCoverage", ";test;jacocoAggregateReport")

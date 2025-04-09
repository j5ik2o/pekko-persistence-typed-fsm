import Dependencies.*

ThisBuild / organization := "com.github.j5ik2o"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.4"

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
    idePackagePrefix := Some("com.github.j5ik2o.eff.sm.splitter"),
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

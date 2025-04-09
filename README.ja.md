# pekko-persistence-typed-fsm

イベントソーシングと状態遷移を Apache Pekko で効率的に実装するためのライブラリです。

*他の言語で読む: [English](README.md)*

## 概要

`pekko-persistence-typed-fsm` は Apache Pekko を使用したイベントソーシングパターンの実装を改善するライブラリです。従来の Pekko Persistence Typed の制約を解消し、より直感的なアクタープログラミングスタイルでイベントソーシングを実現します。

### 主な特徴

- **従来のアクタープログラミングスタイル**: 通常の Behavior ベースのアクタープログラミングスタイルを維持しながらイベントソーシングが可能
- **ドメインロジックの単一実行**: コマンドハンドラでのドメインロジックの二重実行問題を解消
- **DDDとの高い親和性**: ドメインオブジェクトとのシームレスな統合をサポート
- **段階的な実装**: 最初はインメモリモードで開発し、後から永続化対応へ移行可能
- **型安全**: Scala 3 の型システムを活用した型安全な設計

## 背景: なぜこのライブラリが必要か

従来の Pekko Persistence Typed には以下の問題がありました：

1. **従来のアクタープログラミングスタイルとの不一致**: 学習・実装の困難さ
2. **複雑な状態遷移の保守性低下**: match/case の複雑さによるコードの保守性低下
3. **ドメインロジックの二重実行**: コマンドハンドラとイベントハンドラの両方でドメインロジックが実行される

このライブラリは「EventSourcedBehavior を集約アクターの子アクターとして実装する」というアプローチにより、これらの問題を解決します。

## 主要コンポーネント

### Effector

イベントの永続化機能を提供するコアトレイトです。

```scala
trait Effector[S, E, M] {
  def persist(event: E)(onPersisted: (Option[S], E) => Behavior[M]): Behavior[M]
  def persistAll(events: Seq[E])(onPersisted: (Option[S], Seq[E]) => Behavior[M]): Behavior[M]
}
```

### EffectorConfig

Effector の設定を定義するケースクラスです。

```scala
final case class EffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  wrapPersisted: (Option[S], Seq[E]) => M,
  wrapRecovered: S => M,
  unwrapPersisted: M => Option[(Option[S], Seq[E])],
  unwrapRecovered: M => Option[S]
)
```

### MessageConverter

状態(S)、イベント(E)、メッセージ(M)間の相互変換を定義するトレイトです。

```scala
trait MessageConverter[S, E, M <: Matchable] {
  def wrapPersisted(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapRecovered(state: S): M & RecoveredState[S, M]
  // ...
}
```

## 使用例

### 基本的な使い方

```scala
// 1. 状態と状態遷移関数を定義
enum State {
  case NotCreated(aggregateId: EntityId)
  case Created(aggregateId: EntityId, entity: Entity)
  
  def applyEvent(event: Event): State = // 状態遷移の実装
}

// 2. EffectorConfig を設定
val config = EffectorConfig[State, Event, Command](
  persistenceId = entityId.toString,
  initialState = State.NotCreated(entityId),
  applyEvent = (state, event) => state.applyEvent(event),
  messageConverter = Command.messageConverter  // または個別の変換関数を指定
)

// 3. Effector を使用したアクターを作成
Behaviors.setup[Command] { implicit ctx =>
  Effector.create[State, Event, Command](config) {
    case (state: State.NotCreated, effector) =>
      handleNotCreated(state, effector)
    case (state: State.Created, effector) =>
      handleCreated(state, effector)
  }
}

// 4. 状態に応じたハンドラを実装
private def handleCreated(state: State.Created, effector: Effector[State, Event, Command]): Behavior[Command] =
  Behaviors.receiveMessagePartial {
    case Command.DoSomething(id, param, replyTo) =>
      // ドメインロジックを実行
      state.entity.doSomething(param) match {
        case Right((newEntity, event)) =>
          // イベントを永続化
          effector.persist(event) { (newState, _) =>
            replyTo ! Reply.Success(id)
            // 新しい状態で更新
            handleCreated(newState.asInstanceOf[State.Created], effector)
          }
        case Left(error) =>
          replyTo ! Reply.Failed(id, error)
          Behaviors.same
      }
  }
```

より詳細な実装例については、[BankAccountAggregate](src/test/scala/example/BankAccountAggregate.scala) を参照してください。

## インストール方法

build.sbt に以下を追加してください：

```scala
libraryDependencies += "com.github.j5ik2o" %% "pekko-persistence-typed-fsm" % "0.1.0-SNAPSHOT"
```

## ライセンス

このライブラリは Apache License 2.0 の下でライセンスされています。

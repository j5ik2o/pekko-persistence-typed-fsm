# pekko-persistence-effector 

イベントソーシングと状態遷移を Apache Pekko で効率的に実装するためのライブラリです。

*他の言語で読む: [English](README.md)*

## 概要

`pekko-persistence-effector` は Apache Pekko を使用したイベントソーシングパターンの実装を改善するライブラリです。従来の Pekko Persistence Typed の制約を解消し、より直感的なアクタープログラミングスタイルでイベントソーシングを実現します。

### 主な特徴

- **従来のアクタープログラミングスタイル**: 通常の Behavior ベースのアクタープログラミングスタイルを維持しながらイベントソーシングが可能
- **ドメインロジックの単一実行**: コマンドハンドラでのドメインロジックの二重実行問題を解消
- **DDDとの高い親和性**: ドメインオブジェクトとのシームレスな統合をサポート
- **段階的な実装**: 最初はインメモリモードで開発し、後から永続化対応へ移行可能
- **型安全**: Scala 3 の型システムを活用した型安全な設計

## 背景: なぜこのライブラリが必要か

従来の Pekko Persistence Typed には以下の問題がありました：

1. **従来のアクタープログラミングスタイルとの不一致**: 
   - EventSourcedBehavior のパターンを使用することを強制され、通常の Behavior ベースのプログラミングと異なる
   - 学習曲線が急で実装が難しくなる

2. **複雑な状態遷移の保守性低下**: 
   - コマンドハンドラが多数の match/case ステートメントで複雑になる
   - 状態に基づいてハンドラを分割できないため、コードの可読性が低下する

3. **ドメインロジックの二重実行**: 
   - ドメインロジックがコマンドハンドラとイベントハンドラの両方で実行される
   - コマンドハンドラはドメインロジックによって更新された状態を使用できないため、ドメインオブジェクトとの統合がぎこちない

このライブラリは「永続化アクターを集約アクターの子アクターとして実装する」というアプローチにより、これらの問題を解決します。具体的には、ドメインロジックの二重実行を避けるため、内部的にUntyped PersistentActorを使用しています（EventSourcedBehaviorではない）。

## 主要コンポーネント

### PersistenceEffector

イベントの永続化機能を提供するコアトレイトです。

```scala
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
}
```

### InMemoryEffector

イベントとスナップショットをメモリに保存するPersistenceEffectorの実装です。

```scala
final class InMemoryEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
) extends PersistenceEffector[S, E, M]
```

- **主な特徴**:
  - シングルトンの`InMemoryEventStore`を使用してイベントとスナップショットをメモリに保存
  - イベントとスナップショットはメモリに保存されるため、データベース設定なしで高速開発が可能
  - アクター初期化時に保存されたイベントから状態を復元
  - 実際のデータベース操作のレイテンシーなしでの即時永続化
  - 開発、テスト、プロトタイピングフェーズに最適
  - 後で実際の永続化に移行するためのシームレスなパスを提供

### PersistenceEffectorConfig

PersistenceEffector の設定を定義するケースクラスです。

```scala
final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  messageConverter: MessageConverter[S, E, M]
)
```

### MessageConverter

状態(S)、イベント(E)、メッセージ(M)間の相互変換を定義するトレイトです。

```scala
trait MessageConverter[S, E, M <: Matchable] {
  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedState(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]
  // ...
}
```

### Result

ドメイン操作の結果をカプセル化するケースクラスで、新しい状態とイベントを含みます。

```scala
final case class Result[S, E](
  bankAccount: S,
  event: E,
)
```

Resultクラスは以下の主要な利点を提供します：
- **型安全性**: 新しい状態と対応するイベントの関係を明示的に捕捉
- **可読性**: タプルよりも意味が明確で、各値の目的を明示
- **保守性**: パターンマッチングがより明示的になり、変更が容易に
- **ドメインモデリング**: ドメインロジックからの戻り値を標準化

## 使用例

### 銀行口座の例

```scala
// 1. 状態と状態遷移関数を定義
enum State {
  def aggregateId: BankAccountId
  case NotCreated(aggregateId: BankAccountId)
  case Created(aggregateId: BankAccountId, bankAccount: BankAccount)

  def applyEvent(event: BankAccountEvent): State = (this, event) match {
    case (State.NotCreated(aggregateId), BankAccountEvent.Created(id, _)) =>
      Created(id, BankAccount(id))
    case (State.Created(id, bankAccount), BankAccountEvent.CashDeposited(_, amount, _)) =>
      bankAccount
        .add(amount)
        .fold(
          error => throw new IllegalStateException(s"Failed to apply event: $error"),
          result => State.Created(id, result.bankAccount),
        )
    case (State.Created(id, bankAccount), BankAccountEvent.CashWithdrew(_, amount, _)) =>
      bankAccount
        .subtract(amount)
        .fold(
          error => throw new IllegalStateException(s"Failed to apply event: $error"),
          result => State.Created(id, result.bankAccount),
        )
    case _ =>
      throw new IllegalStateException(s"Invalid state transition: $this -> $event")
  }
}

// 2. PersistenceEffectorConfig を設定
val config = PersistenceEffectorConfig[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
  persistenceId = actorName(aggregateId),
  initialState = State.NotCreated(aggregateId),
  applyEvent = (state, event) => state.applyEvent(event),
  messageConverter = BankAccountCommand.messageConverter,
)

// 3. PersistenceEffector を使用したアクターを作成
Behaviors.setup[BankAccountCommand] { implicit ctx =>
  PersistenceEffector.create[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](config) {
    case (initialState: State.NotCreated, effector) =>
      handleNotCreated(initialState, effector)
    case (initialState: State.Created, effector) =>
      handleCreated(initialState, effector)
  }
}

// 4. 状態に応じたハンドラを実装
private def handleNotCreated(
  state: BankAccountAggregate.State.NotCreated,
  effector: PersistenceEffector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
  : Behavior[BankAccountCommand] =
  Behaviors.receiveMessagePartial { case cmd: BankAccountCommand.Create =>
    val Result(bankAccount, event) = BankAccount.create(cmd.aggregateId)
    effector.persistEvent(event) { _ =>
      cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
      handleCreated(State.Created(state.aggregateId, bankAccount), effector)
    }
  }

private def handleCreated(
  state: BankAccountAggregate.State.Created,
  effector: PersistenceEffector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
  : Behavior[BankAccountCommand] =
  Behaviors.receiveMessagePartial {
    case BankAccountCommand.DepositCash(aggregateId, amount, replyTo) =>
      // ドメインロジックを実行
      state.bankAccount
        .add(amount)
        .fold(
          error => {
            replyTo ! DepositCashReply.Failed(aggregateId, error)
            Behaviors.same
          },
          { case Result(newBankAccount, event) =>
            // イベントを永続化
            effector.persistEvent(event) { _ =>
              replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
              // 新しい状態で更新
              handleCreated(state.copy(bankAccount = newBankAccount), effector)
            }
          },
        )
  }
```

## サンプルコードファイル

より詳細な実装例については、以下のファイルを参照してください：

- [BankAccountAggregate](src/test/scala/example/BankAccountAggregate.scala) - PersistenceEffectorを使用したメイン集約実装
- [BankAccount](src/test/scala/example/BankAccount.scala) - 銀行口座のドメインモデル
- [BankAccountCommand](src/test/scala/example/BankAccountCommand.scala) - 集約へのコマンド
- [BankAccountEvent](src/test/scala/example/BankAccountEvent.scala) - 集約から生成されるイベント
- [BankAccountId](src/test/scala/example/BankAccountId.scala) - 銀行口座の識別子
- [Money](src/test/scala/example/Money.scala) - 金額を表す値オブジェクト

## このライブラリを使用するシーン

このライブラリは、特に以下のようなケースに適しています：

- 最初から永続化を考慮せず、段階的に実装したい場合
- 従来のPekko Persistence Typedでは保守が難しい複雑な状態遷移を扱う場合
- ドメインロジックをアクターから分離したいDDD指向の設計を実装する場合
- イベントソーシングアプリケーションに、より自然なアクタープログラミングスタイルが必要な場合

## インストール方法

build.sbt に以下を追加してください：

```scala
resolvers += "GitHub Packages" at
  "https://maven.pkg.github.com/j5ik2o/pekko-persistence-effector"
libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "pekko-persistence-effector" % "..."
)
```

## ライセンス

このライブラリは Apache License 2.0 の下でライセンスされています。

# pekko-persistence-effector

[![CI](https://github.com/j5ik2o/pekko-persistence-effector/workflows/CI/badge.svg)](https://github.com/j5ik2o/pekko-persistence-effector/actions?query=workflow%3ACI)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Tokei](https://tokei.rs/b1/github/j5ik2o/pekko-persistence-dynamodb)](https://github.com/XAMPPRocky/tokei)

イベントソーシングと状態遷移を Apache Pekko で効率的に実装するためのライブラリです。

*他の言語で読む: [English](README.md)*

## 概要

`pekko-persistence-effector` は Apache Pekko を使用したイベントソーシングパターンの実装を改善するライブラリです。従来の Pekko Persistence Typed の制約を解消し、より直感的なアクタープログラミングスタイルでイベントソーシングを実現します。Scala と Java の両方の DSL をサポートしています。

### 主な特徴

- **従来のアクタープログラミングスタイル**: 通常の Behavior ベースのアクタープログラミングスタイルを維持しながらイベントソーシングが可能 (Scala & Java)。
- **ドメインロジックの単一実行**: コマンドハンドラでのドメインロジックの二重実行問題を解消。
- **DDDとの高い親和性**: ドメインオブジェクトとのシームレスな統合をサポート。
- **段階的な実装**: 最初はインメモリモードで開発し、後から永続化対応へ移行可能。
- **型安全**: Scala 3 の型システムを活用した型安全な設計 (Scala DSL)。
- **強化されたエラーハンドリング**: 永続化操作のための設定可能なリトライ機構を含み、一時的な障害に対する回復力を向上。

## 背景: なぜこのライブラリが必要か

従来の Pekko Persistence Typed には以下の問題がありました：

1. **従来のアクタープログラミングスタイルとの不一致**:
   - EventSourcedBehavior のパターンを使用することを強制され、通常の Behavior ベースのプログラミングと異なる。
   - 学習曲線が急で実装が難しくなる。

2. **複雑な状態遷移の保守性低下**:
   - コマンドハンドラが多数の match/case ステートメントで複雑になる。
   - 状態に基づいてハンドラを分割できないため、コードの可読性が低下する。

3. **ドメインロジックの二重実行**:
   - ドメインロジックがコマンドハンドラとイベントハンドラの両方で実行される。
   - コマンドハンドラはドメインロジックによって更新された状態を使用できないため、ドメインオブジェクトとの統合がぎこちない。

このライブラリは「永続化アクターを集約アクターの子アクターとして実装する」というアプローチにより、これらの問題を解決します。具体的には、ドメインロジックの二重実行を避けるため、内部的にUntyped PersistentActorを使用しています（EventSourcedBehaviorではない）。

## 主要コンポーネント

### PersistenceEffector

イベントの永続化機能を提供するコアトレイト (Scala) / インターフェース (Java) です。

```scala
// Scala DSL
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
  // ... リトライロジックを含むメソッド
}
```

```java
// Java DSL
public interface PersistenceEffector<State, Event, Message> {
    Behavior<Message> persistEvent(Event event, Function<Event, Behavior<Message>> onPersisted);
    Behavior<Message> persistEvents(List<Event> events, Function<List<Event>, Behavior<Message>> onPersisted);
    Behavior<Message> persistSnapshot(State snapshot, Function<State, Behavior<Message>> onPersisted);
    // ... リトライロジックを含むメソッド
}
```

### PersistenceEffectorConfig

PersistenceEffector の設定を定義するケースクラス (Scala) / クラス (Java) です。

```scala
// Scala DSL
final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  messageConverter: MessageConverter[S, E, M],
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
  retentionCriteria: Option[RetentionCriteria] = None,
  backoffConfig: Option[BackoffConfig] = None, // PersistenceStoreActor 再起動用
  persistTimeout: FiniteDuration = 30.seconds, // 各永続化試行のタイムアウト
  maxRetries: Int = 3 // 永続化操作の最大リトライ回数
)
```

```java
// Java DSL
public class PersistenceEffectorConfig<State, Event, Message> {
    private final String persistenceId;
    private final State initialState;
    private final BiFunction<State, Event, State> applyEvent;
    private final MessageConverter<State, Event, Message> messageConverter;
    private final PersistenceMode persistenceMode;
    private final int stashSize;
    private final Optional<SnapshotCriteria<State, Event>> snapshotCriteria;
    private final Optional<RetentionCriteria> retentionCriteria;
    private final Optional<BackoffConfig> backoffConfig; // PersistenceStoreActor 再起動用
    private final Duration persistTimeout; // 各永続化試行のタイムアウト
    private final int maxRetries; // 永続化操作の最大リトライ回数
    // コンストラクタとビルダー...
}
```

### MessageConverter

状態(S)、イベント(E)、メッセージ(M)間の相互変換を定義するトレイト (Scala) / インターフェース (Java) です。

```scala
// Scala DSL
trait MessageConverter[S, E, M <: Matchable] {
  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedState(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]
  def wrapPersistFailedAfterRetries(command: Any, cause: Throwable): M & PersistFailedAfterRetries[M]
  // ...
}
```
```java
// Java DSL
public interface MessageConverter<State, Event, Message> {
    Message wrapPersistedEvents(List<Event> events);
    Message wrapPersistedState(State state);
    Message wrapRecoveredState(State state);
    Message wrapPersistFailedAfterRetries(Object command, Throwable cause);
    // アンラッパー...
}
```

### Result

ドメイン操作の結果をカプセル化するケースクラス (Scala) / クラス (Java) で、新しい状態とイベントを含みます。

```scala
// Scala DSL
final case class Result[S, E](
  newState: S, // 明確化のためにリネーム
  event: E,
)
```
```java
// Java DSL
public class Result<State, Event> {
    private final State newState;
    private final Event event;
    // コンストラクタ、ゲッター...
}
```

Resultクラスは以下の主要な利点を提供します：
- **型安全性**: 新しい状態と対応するイベントの関係を明示的に捕捉
- **可読性**: タプルよりも意味が明確で、各値の目的を明示
- **保守性**: パターンマッチングがより明示的になり、変更が容易に
- **ドメインモデリング**: ドメインロジックからの戻り値を標準化

## 使用例

### 銀行口座の例 (Scala DSL)

```scala
// 1. 状態と状態遷移関数を定義
enum State {
  def aggregateId: BankAccountId
  case NotCreated(aggregateId: BankAccountId)
  case Created(aggregateId: BankAccountId, bankAccount: BankAccount)

  def applyEvent(event: BankAccountEvent): State = (this, event) match {
    case (State.NotCreated(aggregateId), BankAccountEvent.Created(id, _)) =>
      Created(id, BankAccount(id))
    // ... 他の遷移
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
  persistenceMode = PersistenceMode.Persisted, // または InMemory
  persistTimeout = 5.seconds,
  maxRetries = 2
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
    effector.persistEvent(event) { _ => // 永続化成功後に実行されるコールバック
      cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
      handleCreated(State.Created(state.aggregateId, bankAccount), effector)
    } // リトライ後に永続化が失敗した場合、MessageConverter経由でPersistFailedAfterRetriesメッセージが送信される
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
          error => { // ドメインバリデーション失敗
            replyTo ! DepositCashReply.Failed(aggregateId, error)
            Behaviors.same
          },
          { case Result(newBankAccount, event) => // ドメインロジック成功
            // イベントを永続化（失敗時はリトライ）
            effector.persistEvent(event) { _ => // 成功時のコールバック
              replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
              // アクターの状態と振る舞いを更新
              handleCreated(state.copy(bankAccount = newBankAccount), effector)
            }
          },
        )
    // 必要に応じて PersistFailedAfterRetries メッセージを処理
    case BankAccountCommand.WrappedPersistFailed(cmd, cause) =>
       // エラーログ記録、元の送信者への通知、アクター停止など
       Behaviors.same
  }
```

*(Java DSL の例は `src/test/java` ディレクトリを参照してください)*

## サンプルコードファイル

より詳細な実装例については、以下のファイルを参照してください：

**Scala DSL:**
- [BankAccountAggregate](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountAggregate.scala)
- [BankAccount](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccount.scala)
- [BankAccountCommand](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountCommand.scala)
- [BankAccountEvent](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountEvent.scala)

**Java DSL:**
- [BankAccountAggregate](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountAggregate.java)
- [BankAccount](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccount.java)
- [BankAccountCommand](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountCommand.java)
- [BankAccountEvent](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountEvent.java)

## このライブラリを使用するシーン

このライブラリは、特に以下のようなケースに適しています：

- 最初から永続化を考慮せず、段階的に実装したい場合。
- 従来のPekko Persistence Typedでは保守が難しい複雑な状態遷移を扱う場合。
- ドメインロジックをアクターから分離したいDDD指向の設計を実装する場合。
- イベントソーシングアプリケーションに、より自然なアクタープログラミングスタイルが必要な場合 (Scala または Java)。
- 一時的な永続化失敗に対する回復力が必要な場合。

## インストール方法

注意: 本ライブラリは `pekko-persistence-typed` に依存していません。`pekko-persistence-typed` を依存関係に追加しなくても本ライブラリを使用できます。

`build.sbt` に以下を追加してください：

```scala
resolvers += "GitHub Packages" at
  "https://maven.pkg.github.com/j5ik2o/pekko-persistence-effector"

libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "pekko-persistence-effector" % "<最新バージョン>"
)
```

または Maven (`pom.xml`) の場合:
```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/j5ik2o/pekko-persistence-effector</url>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.j5ik2o</groupId>
    <artifactId>pekko-persistence-effector_3</artifactId> <!-- または _2.13 -->
    <version>LATEST</version> <!-- 特定のバージョンに置き換えてください -->
  </dependency>
</dependencies>
```
*(GitHub Packages の認証設定を忘れずに行ってください)*

## ライセンス

このライブラリは Apache License 2.0 の下でライセンスされています。

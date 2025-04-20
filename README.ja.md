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

PersistenceEffector の設定を定義するトレイト (Scala) / クラス (Java) です。

```scala
// Scala DSL
trait PersistenceEffectorConfig[S, E, M] {
  def persistenceId: String
  def initialState: S
  def applyEvent: (S, E) => S
  def persistenceMode: PersistenceMode
  def stashSize: Int
  def snapshotCriteria: Option[SnapshotCriteria[S, E]]
  def retentionCriteria: Option[RetentionCriteria]
  def backoffConfig: Option[BackoffConfig] // PersistenceStoreActor 再起動用
  def messageConverter: MessageConverter[S, E, M]
  // ... その他のメソッド
}
```

```java
// Java DSL
public class PersistenceEffectorConfig<State, Event, Message> {
    private final String persistenceId;
    private final State initialState;
    private final BiFunction<State, Event, State> applyEvent;
    private final PersistenceMode persistenceMode;
    private final int stashSize;
    private final Optional<SnapshotCriteria<State, Event>> snapshotCriteria;
    private final Optional<RetentionCriteria> retentionCriteria;
    private final Optional<BackoffConfig> backoffConfig; // PersistenceStoreActor 再起動用
    private final MessageConverter<State, Event, Message> messageConverter;
    // コンストラクタとビルダー...
}
```

## 使用例

### 銀行口座の例 (Scala DSL)

pekko-persistence-effectorを使用した銀行口座集約の完全な実装例を以下に示します：

```scala
// 1. Scala 3の機能を活用したドメインモデル、コマンド、イベント、応答の定義

// ドメインモデル
final case class BankAccountId(value: String)
final case class Money(amount: BigDecimal)
final case class BankAccount(id: BankAccountId, balance: Money = Money(0)) {
  def deposit(amount: Money): Either[BankAccountError, (BankAccount, BankAccountEvent)] = {
    if (amount.amount <= 0) {
      Left(BankAccountError.InvalidAmount)
    } else {
      Right((copy(balance = Money(balance.amount + amount.amount)), 
             BankAccountEvent.Deposited(id, amount, Instant.now())))
    }
  }
                
  def withdraw(amount: Money): Either[BankAccountError, (BankAccount, BankAccountEvent)] = {
    if (amount.amount <= 0) {
      Left(BankAccountError.InvalidAmount)
    } else if (balance.amount < amount.amount) {
      Left(BankAccountError.InsufficientFunds)
    } else {
      Right((copy(balance = Money(balance.amount - amount.amount)),
             BankAccountEvent.Withdrawn(id, amount, Instant.now())))
    }
  }
}

// コマンド
enum BankAccountCommand {
  case Create(id: BankAccountId, replyTo: ActorRef[CreateReply])
  case Deposit(id: BankAccountId, amount: Money, replyTo: ActorRef[DepositReply])
  case Withdraw(id: BankAccountId, amount: Money, replyTo: ActorRef[WithdrawReply])
  case GetBalance(id: BankAccountId, replyTo: ActorRef[GetBalanceReply])
}

// イベント
enum BankAccountEvent {
  def id: BankAccountId
  def occurredAt: Instant
  
  case Created(id: BankAccountId, occurredAt: Instant) extends BankAccountEvent
  case Deposited(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
  case Withdrawn(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
}

// 応答メッセージ
enum CreateReply {
  case Succeeded(id: BankAccountId)
  case Failed(id: BankAccountId, error: BankAccountError)
}

enum DepositReply {
  case Succeeded(id: BankAccountId, amount: Money)
  case Failed(id: BankAccountId, error: BankAccountError)
}

// エラー型
enum BankAccountError {
  case InvalidAmount
  case InsufficientFunds
  case AlreadyExists
  case NotFound
}

// 2. 集約アクターの定義
object BankAccountAggregate {
  // enumを使用した状態定義
  enum State {
    def id: BankAccountId
    
    case NotCreated(id: BankAccountId) extends State
    case Active(id: BankAccountId, account: BankAccount) extends State
    
    // イベント適用ロジック
    def applyEvent(event: BankAccountEvent): State = (this, event) match {
      case (NotCreated(id), BankAccountEvent.Created(_, _)) =>
        Active(id, BankAccount(id))
        
      case (active: Active, evt: BankAccountEvent.Deposited) =>
        val newAccount = active.account.copy(
          balance = Money(active.account.balance.amount + evt.amount.amount)
        )
        active.copy(account = newAccount)
        
      case (active: Active, evt: BankAccountEvent.Withdrawn) =>
        val newAccount = active.account.copy(
          balance = Money(active.account.balance.amount - evt.amount.amount)
        )
        active.copy(account = newAccount)
        
      case _ =>
        throw IllegalStateException(s"Invalid state transition: $this -> $event")
    }
  }
  
  // アクターファクトリ
  def apply(id: BankAccountId): Behavior[BankAccountCommand] = {
    Behaviors.setup { context =>
      // PersistenceEffector設定の作成
      val config = PersistenceEffectorConfig[State, BankAccountEvent, BankAccountCommand](
        persistenceId = s"bank-account-${id.value}",
        initialState = State.NotCreated(id),
        applyEvent = (state, event) => state.applyEvent(event),
        persistenceMode = PersistenceMode.Persisted, // 開発時はInMemoryも選択可能
        stashSize = 100
      )
      
      // PersistenceEffectorの作成
      PersistenceEffector.fromConfig(config) {
        case (state: State.NotCreated, effector) => handleNotCreated(state, effector)
        case (state: State.Active, effector) => handleActive(state, effector)
      }
    }
  }
  
  // NotCreated状態のハンドラ
  private def handleNotCreated(
    state: State.NotCreated,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Create =>
        // 新しいアカウントを作成しイベントを生成
        val event = BankAccountEvent.Created(cmd.id, Instant.now())
        
        // イベントを永続化
        effector.persistEvent(event) { _ =>
          // 永続化成功後、応答を返し振る舞いを変更
          cmd.replyTo ! CreateReply.Succeeded(cmd.id)
          handleActive(State.Active(cmd.id, BankAccount(cmd.id)), effector)
        }
    }
  }
  
  // Active状態のハンドラ
  private def handleActive(
    state: State.Active,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Deposit =>
        // ドメインロジックを実行
        state.account.deposit(cmd.amount) match {
          case Left(error) =>
            // ドメインバリデーション失敗
            cmd.replyTo ! DepositReply.Failed(cmd.id, error)
            Behaviors.same
            
          case Right((newAccount, event)) =>
            // イベントを永続化
            effector.persistEvent(event) { _ =>
              // 永続化成功後、応答を返し状態を更新
              cmd.replyTo ! DepositReply.Succeeded(cmd.id, cmd.amount)
              handleActive(state.copy(account = newAccount), effector)
            }
        }
        
      case cmd: BankAccountCommand.Withdraw =>
        // Depositと同様の実装...
        
      case cmd: BankAccountCommand.GetBalance =>
        // 読み取り専用操作、永続化不要
        cmd.replyTo ! GetBalanceReply.Success(cmd.id, state.account.balance)
        Behaviors.same
    }
  }
}
```

この例では以下を示しています：
1. バリデーションロジックを持つドメインモデル
2. コマンド、イベント、応答メッセージの定義
3. イベント適用ロジックを持つ状態定義
4. PersistenceEffectorの設定と作成
5. 状態固有のメッセージハンドラ
6. ドメインバリデーションと永続化失敗の両方に対するエラー処理

*(Java DSL の例は `src/test/java` ディレクトリを参照してください)*

## サンプルコードファイル

より詳細な実装例については、以下のファイルを参照してください：

**Scala DSL:**
- 集約: [BankAccountAggregate.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountAggregate.scala)
- ドメインモデル: [BankAccount.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccount.scala)
- コマンド: [BankAccountCommand.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountCommand.scala)
- イベント: [BankAccountEvent.scala](src/test/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountEvent.scala)

**Java DSL:**
- 集約: [BankAccountAggregate.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountAggregate.java)
- ドメインモデル: [BankAccount.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccount.java)
- コマンド: [BankAccountCommand.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountCommand.java)
- イベント: [BankAccountEvent.java](src/test/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountEvent.java)

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

# pekko-persistence-effector 実装パターンとドメイン駆動設計

本ドキュメントでは、eff-sm-splitterのサンプル実装であるBankAccountを通じて、このライブラリが提供する実装パターンとドメイン駆動設計（DDD）の実践方法について詳細に分析します。

## 実装パターンの全体像

eff-sm-splitterでは、以下の実装パターンが採用されています：

1. **ドメインモデルとアクターの分離**
   - ドメインロジックを純粋な関数型スタイルで実装
   - アクターはドメインロジックの実行とイベント永続化のコーディネーターとして機能

2. **Resultパターン**
   - ドメインオペレーションの結果を状態とイベントのペアとして表現
   - 副作用（イベントの永続化）を遅延させるための明示的なデータ構造

3. **状態遷移のパターン**
   - 列挙型による状態の型安全な表現
   - 状態に依存したハンドラメソッドの分割
   - イベント駆動型の状態更新

4. **イベントソーシング**
   - コマンド → イベント → 状態 の明確な分離
   - イベント適用ロジックの集中管理

5. **モード切替可能なデザイン**
   - InMemoryモードとPersistedモードの簡単な切り替え
   - 実装初期段階ではInMemoryモードでスタート可能

## ドメインモデルの設計

### BankAccount（銀行口座）の例

BankAccountの実装は、ドメイン駆動設計の考え方に基づいています。

```scala
final case class BankAccount(
  bankAccountId: BankAccountId,
  limit: Money = Money(100000, Money.JPY),
  balance: Money = Money(0, Money.JPY),
) {
  def add(amount: Money): Either[BankAccountError, Result[BankAccount, BankAccountEvent]] =
    if (limit < (balance + amount))
      Left(BankAccountError.LimitOverError)
    else
      Right(
        Result(
          copy(balance = balance + amount),
          BankAccountEvent.CashDeposited(bankAccountId, amount, Instant.now())))

  def subtract(amount: Money): Either[BankAccountError, Result[BankAccount, BankAccountEvent]] =
    // ...
}
```

**特徴**:
- 不変オブジェクト（immutable）として設計
- ビジネスルール（入金上限、残高チェック）をカプセル化
- すべての操作は新しいインスタンスを返す（副作用なし）
- 操作の結果は「新しい状態」と「発生したイベント」の両方を含む

### Resultパターン

```scala
final case class Result[S, E](
  bankAccount: S,
  event: E,
)
```

**特徴**:
- 型パラメータSは状態、Eはイベントを表す
- ドメイン操作の結果を状態とイベントのペアで表現
- 副作用（イベント永続化）を遅延させるための明示的構造
- タプルではなく意味のある名前で構造化

### イベント設計

```scala
enum BankAccountEvent {
  def aggregateId: BankAccountId
  def occurredAt: Instant

  case Created(aggregateId: BankAccountId, occurredAt: Instant)
  case CashDeposited(aggregateId: BankAccountId, amount: Money, occurredAt: Instant)
  case CashWithdrew(aggregateId: BankAccountId, amount: Money, occurredAt: Instant)
}
```

**特徴**:
- 列挙型（enum）を使って型安全に定義
- イベントごとに必要な情報をパラメータとして定義
- 全イベントに共通のメタデータ（集約ID、発生時間）を定義
- 名詞ではなく動詞の過去形でイベントを命名

## 集約（Aggregate）の実装

銀行口座の集約アクターは、ドメインモデルとイベント永続化の橋渡し役として設計されています：

```scala
object BankAccountAggregate {
  // 状態の定義
  enum State {
    def aggregateId: BankAccountId
    case NotCreated(aggregateId: BankAccountId)
    case Created(aggregateId: BankAccountId, bankAccount: BankAccount)
    
    def applyEvent(event: BankAccountEvent): State = ...
  }

  def apply(aggregateId: BankAccountId, persistenceMode: PersistenceMode = PersistenceMode.Persisted): Behavior[BankAccountCommand] = {
    val config = PersistenceEffectorConfig[State, BankAccountEvent, BankAccountCommand](...)
    Behaviors.setup[BankAccountCommand] { implicit ctx =>
      PersistenceEffector.create[State, BankAccountEvent, BankAccountCommand](config) {
        case (initialState: State.NotCreated, effector) => handleNotCreated(initialState, effector)
        case (initialState: State.Created, effector) => handleCreated(initialState, effector)
      }
    }
  }
  
  private def handleNotCreated(state: State.NotCreated, effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]): Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial { case cmd: BankAccountCommand.Create =>
      val Result(bankAccount, event) = BankAccount.create(cmd.aggregateId)
      effector.persistEvent(event) { _ =>
        cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
        handleCreated(State.Created(state.aggregateId, bankAccount), effector)
      }
    }
    
  private def handleCreated(state: State.Created, effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]): Behavior[BankAccountCommand] =
    // ...
}
```

**特徴**:
1. **状態の型安全な表現**:
   - 列挙型（enum）による状態の明示的な型定義
   - 状態ごとに保持すべき情報を定義
   - アクターの状態遷移をコンパイル時に型チェック可能

2. **状態に応じたハンドラの分割**:
   - 状態ごとに別のハンドラメソッド（handleNotCreated, handleCreated）
   - 各状態で受け付け可能なコマンドを型安全に制限
   - 状態ごとの振る舞いを明確に分離

3. **イベント永続化と状態遷移の連携**:
   - ドメインモデルでロジックを実行し、Resultオブジェクトを取得
   - effector.persistEventでイベントを永続化
   - コールバック内で明示的に新しい状態への遷移を定義

4. **明示的なコマンド応答**:
   - 各コマンド処理の後に適切な応答メッセージを送信
   - 成功／失敗ケースを明示的に処理
   - デッドレターを防止する設計

## コマンド設計

```scala
enum BankAccountCommand {
  case GetBalance(override val aggregateId: BankAccountId, replyTo: ActorRef[GetBalanceReply])
  case Stop(override val aggregateId: BankAccountId, replyTo: ActorRef[StopReply])
  case Create(override val aggregateId: BankAccountId, replyTo: ActorRef[CreateReply])
  case DepositCash(override val aggregateId: BankAccountId, amount: Money, replyTo: ActorRef[DepositCashReply])
  case WithdrawCash(override val aggregateId: BankAccountId, amount: Money, replyTo: ActorRef[WithdrawCashReply])
  
  // 内部メッセージ
  private case StateRecovered(state: BankAccountAggregate.State) extends ... with RecoveredState[...]
  private case EventPersisted(events: Seq[BankAccountEvent]) extends ... with PersistedEvent[...]
  // ...
  
  def aggregateId: BankAccountId = ...
}
```

**特徴**:
- すべてのコマンドに集約IDを含める
- 応答チャネル（replyTo）を明示的に指定
- 応答メッセージの型を明示的に定義
- 内部メッセージをprivateとして保護

## 応答メッセージ設計

```scala
enum CreateReply {
  case Succeeded(aggregateId: BankAccountId)
}

enum DepositCashReply {
  case Succeeded(aggregateId: BankAccountId, amount: Money)
  case Failed(aggregateId: BankAccountId, error: BankAccountError)
}
```

**特徴**:
- 列挙型で成功/失敗ケースを型安全に表現
- 集約IDを常に含めて送信元を追跡可能に
- 必要な情報（金額、エラー情報など）を含める
- コマンドごとに専用の応答型を定義

## ドメイン駆動設計の実践

eff-sm-splitterのサンプル実装は、以下のDDDの原則に従っています：

### 1. 集約（Aggregate）

BankAccountAggregateは集約ルートとして機能し：
- 銀行口座に関連するすべての操作を調整
- 整合性境界を形成（すべての更新は集約ルートを通じて）
- 識別子（BankAccountId）によって一意に識別

### 2. 値オブジェクト（Value Object）

`Money`は値オブジェクトとして実装：
- 不変
- 等価性に基づく比較
- 自己完結型の振る舞い（加算、減算など）

### 3. エンティティ（Entity）

`BankAccount`はエンティティとして実装：
- 一意の識別子（bankAccountId）を持つ
- ライフサイクルを通じて同一性を維持
- 状態変更操作を持つ

### 4. ドメインイベント（Domain Event）

`BankAccountEvent`はドメインイベントとして実装：
- 過去に発生したことの記録
- 発生時刻と関連する集約IDを含む
- ビジネス上の重要な変更を表現

### 5. コマンド（Command）

`BankAccountCommand`はコマンドとして実装：
- システムに対する意図を表現
- トレーサビリティのための集約IDを含む
- 応答チャネルを指定

### 6. リポジトリ（Repository）

`PersistenceEffector`がリポジトリの役割：
- イベントの取得と永続化を抽象化
- 集約の再構築メカニズムを提供
- ストレージの詳細を隠蔽

## ユースケース実装のパターン

サンプル実装では以下のような一般的なユースケースが示されています：

### 1. 集約の作成

```scala
private def handleNotCreated(state: State.NotCreated, effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]): Behavior[BankAccountCommand] =
  Behaviors.receiveMessagePartial { case cmd: BankAccountCommand.Create =>
    val Result(bankAccount, event) = BankAccount.create(cmd.aggregateId)
    effector.persistEvent(event) { _ =>
      cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
      handleCreated(State.Created(state.aggregateId, bankAccount), effector)
    }
  }
```

**特徴**:
- 初期状態では限定的なコマンドのみ受け付け
- ドメインロジックを実行して新しいエンティティとイベントを取得
- イベント永続化後に新しい状態へ遷移

### 2. 状態変更操作

```scala
// handleCreatedメソッド内のコード例
case BankAccountCommand.DepositCash(aggregateId, amount, replyTo) =>
  state.bankAccount
    .add(amount)
    .fold(
      error => {
        replyTo ! DepositCashReply.Failed(aggregateId, error)
        Behaviors.same
      },
      { case Result(newBankAccount, event) =>
        effector.persistEvent(event) { _ =>
          replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
          handleCreated(state.copy(bankAccount = newBankAccount), effector)
        }
      },
    )
```

**特徴**:
- Eitherを使った成功/失敗の処理
- 失敗時は状態を変更せず、エラー応答を返す
- 成功時はイベントを永続化し、状態を更新
- 状態更新は既存のアクター状態を変更せず、新しい状態への遷移を表現

### 3. 照会操作

```scala
case BankAccountCommand.GetBalance(aggregateId, replyTo) =>
  replyTo ! GetBalanceReply.Succeeded(aggregateId, state.bankAccount.balance)
  Behaviors.same
```

**特徴**:
- 読み取り専用操作はイベントを発生させない
- 即時応答
- Behaviors.sameで状態を変更しないことを明示

### 4. 集約の停止

```scala
case BankAccountCommand.Stop(aggregateId, replyTo) =>
  replyTo ! StopReply.Succeeded(aggregateId)
  Behaviors.stopped
```

**特徴**:
- アクターを明示的に停止するコマンド
- 停止前に成功応答を送信
- Behaviors.stoppedを使用

## テストパターン

BankAccountサンプルのテストでは、以下のパターンが使用されています：

### 1. InMemoryモードでのテスト

```scala
class InMemoryBankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.InMemory
  
  // テスト終了時にInMemoryStoreをクリア
  override def afterAll(): Unit = {
    InMemoryEventStore.clear()
    super.afterAll()
  }
}
```

**特徴**:
- 実際のデータベースなしでテスト可能
- テスト間の分離のためのイベントストアのクリアアップ
- 同じテストケースをPersistedモードでも実行可能

### 2. コンテキスト・スペック・パターン

```scala
s"BankAccountAggregate with ${persistenceMode} mode" should {
  "create a new bank account successfully" in {
    // テストロジック
  }
  
  "deposit cash successfully" in {
    // テストロジック
  }
  
  // 他のテストケース
}
```

**特徴**:
- コンテキスト（BankAccountAggregate with ${persistenceMode} mode）を明示
- 動作仕様を自然言語で表現
- テストケースごとに独立したブロック

### 3. アクターテストキットの活用

```scala
val bankAccountActor = spawn(createBankAccountAggregate(accountId))

val probe = createTestProbe[CreateReply]()
bankAccountActor ! BankAccountCommand.Create(accountId, probe.ref)

val response = probe.expectMessageType[CreateReply.Succeeded]
response.aggregateId shouldBe accountId
```

**特徴**:
- TestProbeを使用した応答の検証
- ActorTestKitによるアクター生成と管理
- 型安全なメッセージ期待パターン

### 4. 複合シナリオテスト

```scala
"maintain state after stop and restart with multiple actions" in {
  // 最初のアクターを作成して状態を構築
  val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))
  
  // 口座作成
  // 預金
  // もう一度預金
  // アクター停止
  
  // 2番目のアクターを作成
  val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))
  
  // 残高確認 - 前のアクターの状態が復元されていることを確認
  // アクター再起動後も正常に操作できることを確認
  // 最終残高確認
}
```

**特徴**:
- 複数のステップを含む完全なユースケースのテスト
- アクターの停止と再起動を含むシナリオ
- 永続化と状態復元の検証

## 実装上の留意点

サンプル実装から抽出できる重要な実装上のポイント：

### 1. 純粋なドメインモデル
- ドメインロジックをアクターから分離し、純粋関数として実装
- 副作用（イベント永続化）を明示的に分離
- ドメインモデルの再利用性と単体テスト容易性の向上

### 2. 明示的な状態遷移
- 状態を列挙型で明示的に表現
- 状態ごとのハンドラメソッドの分割
- コールバックベースのBehavior返却による状態遷移

### 3. イベントファースト設計
- ドメインオペレーションは状態変更とイベントを共に返す
- イベントは「何が起きたか」を表現
- イベントから現在の状態を再構築可能

### 4. Eitherによるエラー処理
- ドメインエラーを専用の型で表現
- Either[Error, Result]パターンの一貫した使用
- 失敗ケースの明示的な処理

### 5. テスト駆動型API設計
- テストファーストで使いやすいAPIを設計
- InMemoryモードによる迅速な開発サイクル
- クリーンなテストコードによる実装意図の明確化

## まとめ

eff-sm-splitterのサンプル実装は、以下の点で優れたドメイン駆動設計の実践例を示しています：

1. **関心の分離**: ドメインロジック、イベント永続化、メッセージハンドリングの明確な分離
2. **型安全性**: 列挙型、Either、ジェネリクスを活用した型安全な実装
3. **明示的な設計**: 状態、イベント、コマンド、応答の明示的な設計
4. **テスト容易性**: 実装から永続化を分離することによるテスト容易性の向上
5. **フレキシブルなモード**: インメモリモードと永続化モードの切り替えによる開発柔軟性

このアプローチにより、ドメインロジックとアクターモデルを自然に統合し、イベントソーシングパターンを実装する際の複雑さを軽減しています。特に「状態遷移に応じたハンドラメソッドの分割」と「Resultパターンによるドメインロジックの明示的な返却」は、複雑なビジネスロジックを持つアプリケーションにおいて高い保守性と拡張性をもたらす優れた設計パターンです。

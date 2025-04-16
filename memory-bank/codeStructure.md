# eff-sm-splitter コード構造解析

## コア構成要素

### コンポーネント階層

```
PersistenceEffector (trait)
├── DefaultPersistenceEffector (class) - 実際の永続化実装
└── InMemoryEffector (class) - インメモリ実装
```

### 主要クラス・トレイトと役割

| クラス/トレイト | 役割 | 主要機能 |
|-----------------|------|----------|
| `PersistenceEffector` | イベント永続化の中核インターフェース | イベント/スナップショットの永続化 |
| `PersistenceEffectorConfig` | Effectorの設定を定義 | 永続化ID、イベント適用関数などの設定 |
| `MessageConverter` | 型変換の抽象化 | 状態/イベント/メッセージ間の変換 |
| `MessageWrapper` | メッセージの型付けサポート | タイプセーフなメッセージハンドリング |
| `InMemoryEffector` | メモリ内の実装 | 開発/テスト向けの永続化機能 |
| `DefaultPersistenceEffector` | 実際のDBを使用する実装 | Pekko Persistenceを活用した永続化 |
| `SnapshotCriteria` | スナップショット戦略の定義 | スナップショット取得条件の設定 |
| `RetentionCriteria` | スナップショット保持ポリシー | 古いスナップショットの削除管理 |

## 詳細コード分析

### PersistenceEffector

```scala
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
  // その他のメソッド...
}
```

- **型パラメータ**:
  - `S`: 状態の型
  - `E`: イベントの型
  - `M`: メッセージの型
- **主要メソッド**:
  - `persistEvent`: 単一イベントの永続化
  - `persistEvents`: 複数イベントの永続化
  - `persistSnapshot`: スナップショットの永続化
  - `persistEventWithState`: イベント永続化とスナップショット評価
  - `persistEventsWithState`: 複数イベント永続化とスナップショット評価

#### 特筆すべき設計パターン:
- コールバックベースのAPI設計 (`onPersisted` 関数)
- 型パラメータによる柔軟性と型安全性
- コマンドパターンとイベントソーシングの融合

### PersistenceEffectorConfig

```scala
final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  messageConverter: MessageConverter[S, E, M],
  persistenceMode: PersistenceMode,
  stashSize: Int,
  snapshotCriteria: Option[SnapshotCriteria[S, E]] = None,
  retentionCriteria: Option[RetentionCriteria] = None,
)
```

- **機能**:
  - 永続化ID、初期状態、イベント適用ロジックなどの基本設定
  - `MessageConverter`との統合によるボイラープレートコードの削減
  - イベント/状態の変換関数へのアクセスをラップ

### MessageConverter

```scala
trait MessageConverter[S, E, M] {
  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedSnapshot(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]
  def wrapDeleteSnapshots(maxSequenceNumber: Long): M & DeletedSnapshots[M]
  // アンラップ関連メソッド...
}
```

- **機能**:
  - Scala 3の交差型を活用した型安全な変換
  - ドメインイベント/状態からアクターメッセージへの変換
  - パターンマッチングと型キャストによる安全な復元

### MessageWrapper

```scala
sealed trait MessageWrapper[M] { self: M => }

trait PersistedEvent[E, M] extends MessageWrapper[M] { self: M =>
  def events: Seq[E]
}

trait PersistedState[S, M] extends MessageWrapper[M] { self: M =>
  def state: S
}

trait RecoveredState[S, M] extends MessageWrapper[M] { self: M =>
  def state: S
}

trait DeletedSnapshots[M] extends MessageWrapper[M] { self: M =>
  def maxSequenceNumber: Long
}
```

- **設計パターン**:
  - 自己型注釈（self-type annotation）による型合成
  - マーカーインターフェースによるメッセージ分類
  - タイプセーフなパターンマッチングのサポート

### インメモリ実装 (InMemoryEffector)

```scala
final class InMemoryEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
) extends PersistenceEffector[S, E, M]
```

- **特徴**:
  - `InMemoryEventStore`シングルトンを使用したメモリ内永続化
  - イベント/スナップショットの非同期な振る舞いのエミュレーション
  - 実DBを使わない開発/テスト用の軽量な実装

#### InMemoryEventStore

```scala
private[effector] object InMemoryEventStore {
  // スレッドセーフなコレクション
  private val events: scala.collection.mutable.Map[String, Vector[Any]] = ...
  private val snapshots: scala.collection.mutable.Map[String, Any] = ...
  // その他のコレクション...
  
  // 操作メソッド
  def addEvent[E](id: String, event: E): Unit = ...
  def addEvents[E](id: String, newEvents: Seq[E]): Unit = ...
  def saveSnapshot[S](id: String, snapshot: S): Unit = ...
  // 他のメソッド...
}
```

- **重要なポイント**:
  - スレッドセーフなコレクションを使用
  - ID（永続化ID）ベースのイベント/スナップショット管理
  - リプレイロジックの実装

### 本番実装 (DefaultPersistenceEffector)

```scala
final class DefaultPersistenceEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
  persistenceRef: ActorRef[PersistenceCommand[S, E]],
  adapter: ActorRef[PersistenceReply[S, E]])
  extends PersistenceEffector[S, E, M]
```

- **実装の特徴**:
  - 内部にPersistentActorを使用
  - メッセージアダプタによる型変換の自動化
  - 永続化コマンドとコールバック処理のコーディネーション
  - スタッシュバッファによるメッセージキューイング

#### 永続化フロー:
1. PersistenceRefへコマンド送信
2. Adapterを通じた永続化完了通知の受信
3. `waitForMessage`によるメッセージ待機
4. 成功時のコールバック実行と新しいBehaviorの生成

## 実装例: BankAccountAggregate

```scala
object BankAccountAggregate {
  // 状態の定義
  enum State {
    def aggregateId: BankAccountId
    case NotCreated(aggregateId: BankAccountId)
    case Created(aggregateId: BankAccountId, bankAccount: BankAccount)
    
    def applyEvent(event: BankAccountEvent): State = ...
  }

  // アクター生成
  def apply(aggregateId: BankAccountId, persistenceMode: PersistenceMode = PersistenceMode.Persisted): Behavior[BankAccountCommand] = {
    val config = PersistenceEffectorConfig[...]
    Behaviors.setup[BankAccountCommand] { implicit ctx =>
      PersistenceEffector.create[...](config) {
        case (initialState: State.NotCreated, effector) => handleNotCreated(initialState, effector)
        case (initialState: State.Created, effector) => handleCreated(initialState, effector)
      }
    }
  }
  
  // 状態ごとのハンドラメソッド
  private def handleNotCreated(...): Behavior[BankAccountCommand] = ...
  private def handleCreated(...): Behavior[BankAccountCommand] = ...
}
```

### 状態遷移の特徴:
- 列挙型による状態の型安全な表現
- 状態に応じたハンドラメソッドの分割
- Behaviorを返すコールバック関数による明示的な状態遷移

### メッセージプロトコル:

```scala
enum BankAccountCommand {
  case GetBalance(override val aggregateId: BankAccountId, replyTo: ActorRef[GetBalanceReply])
  case Stop(override val aggregateId: BankAccountId, replyTo: ActorRef[StopReply])
  case Create(override val aggregateId: BankAccountId, replyTo: ActorRef[CreateReply])
  // その他のコマンド...
  
  // 内部メッセージ
  private case StateRecovered(state: BankAccountAggregate.State) extends ... with RecoveredState[...]
  private case EventPersisted(events: Seq[BankAccountEvent]) extends ... with PersistedEvent[...]
  // その他の内部メッセージ...
}
```

- **設計ポイント**:
  - コマンドとイベントの明確な分離
  - レスポンスメッセージの型安全な定義
  - メッセージコンバーターとの連携

## 永続化モードとスナップショット戦略

### PersistenceMode

```scala
enum PersistenceMode {
  case Persisted
  case InMemory
}
```

- **利点**: 
  - 単一の設定パラメータで実装を切り替え可能
  - テスト・開発・本番環境の柔軟な設定

### SnapshotCriteria

```scala
trait SnapshotCriteria[S, E] {
  def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long): Boolean
}
```

- **実装例**:
  - `SnapshotCriteria.every(n)`: n個のイベントごとにスナップショット
  - カスタム条件に基づくスナップショット戦略

### RetentionCriteria

```scala
final case class RetentionCriteria(
  snapshotEvery: Option[Int] = None,
  keepNSnapshots: Option[Int] = None,
)
```

- **機能**:
  - 古いスナップショットの自動削除
  - ストレージ使用量の最適化
  - 不要なデータの継続的なクリーンアップ

## テスト戦略

テストでは、主に以下のアプローチを採用:

1. **InMemoryEffectorを使用した単体テスト**:
   - 永続化ロジックのモック不要
   - 実際の永続化の挙動を模倣しつつ高速なテスト実行

2. **実際のPersistenceを使用した統合テスト**:
   - インメモリジャーナルを使用
   - 実際の永続化・復元フローの検証

3. **スナップショット戦略のテスト**:
   - 条件に基づくスナップショット作成の検証
   - 保持ポリシーに基づく削除の検証

## まとめ

eff-sm-splitterは、イベントソーシングパターンを通常のアクタープログラミングスタイルで実装できるよう設計された柔軟なライブラリです。特にPekkoの標準的なEventSourcedBehaviorとは異なり、集約アクター内で通常のBehaviorベースのプログラミングを維持しつつイベント永続化を実現しています。

また、InMemoryEffectorの提供により、永続化を考慮しないステップバイステップの実装が可能となり、開発初期段階での柔軟性が大幅に向上します。

コードベースは高度に型安全なAPIを提供し、Scala 3の最新機能（交差型、列挙型など）を活用して、タイプセーフなイベントソーシングの実装を支援します。

# eff-sm-splitter 技術コンテキスト

## 技術概要

eff-sm-splitterは、Apache Pekkoを基盤としたイベントソーシングパターン実装支援ライブラリです。従来のPekko Persistence Typedの制約を解消し、より直感的なアクタープログラミングスタイルでイベントソーシングを実現します。具体的には、Untyped PersistentActorを集約アクターの子アクターとして実装することで、通常のBehaviorベースのプログラミングパラダイムを維持しつつ永続化機能を提供します。

## 技術アーキテクチャ

### コア設計原則

1. **分離された責務**
   - 集約（アクター）とイベント永続化の責務を分離
   - 状態管理とイベント管理を明確に区分け

2. **型安全性**
   - Scala 3の型システムを活用した型安全な設計
   - 型パラメータによる型制約と型推論の活用

3. **拡張性と柔軟性**
   - 様々なドメインモデルに適用可能な汎用設計
   - インメモリモードと永続化モードの切り替えが容易

### 主要コンポーネントと技術詳細

#### 1. PersistenceEffector

永続化機能の中核となるトレイトおよびその実装です。

```scala
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
}
```

- **技術的実装**:
  - `persistEvent`メソッドは単一イベントの永続化を担当
  - `persistEvents`メソッドは複数イベントのバッチ永続化を担当
  - `persistSnapshot`メソッドはスナップショットの永続化を担当
  - 内部的にはUntyped PersistentActorを使用（ドメインロジックの二重実行を避けるため）
  - 永続化後のコールバック処理を型安全に実行

- **技術的特徴**:
  - コールバック関数がBehavior[M]を返すことで状態遷移と連動
  - メッセージスタッシングによる永続化中のメッセージ管理
  - 内部的な子アクターとしてUntyped PersistentActorを実装

#### 2. PersistenceEffectorConfig

PersistenceEffectorの設定と動作を定義するための構成クラスです。

```scala
final case class PersistenceEffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  wrapPersistedEvents: Seq[E] => M,
  wrapPersistedSnapshot: S => M,
  wrapRecoveredState: S => M,
  unwrapPersistedEvents: M => Option[Seq[E]],
  unwrapPersistedSnapshot: M => Option[S],
  unwrapRecoveredState: M => Option[S],
  stashSize: Int = 32,
)
```

- **技術的実装**:
  - 型パラメータS（状態）、E（イベント）、M（メッセージ）を持つジェネリックなケースクラス
  - `applyEvent`関数によるイベント適用ロジックの定義
  - MessageConverterとの統合によるボイラープレートコードの削減

#### 3. MessageConverter

状態、イベント、メッセージ間の変換を定義するトレイトです。

```scala
trait MessageConverter[S, E, M <: Matchable] {
  def wrapPersistedEvents(events: Seq[E]): M & PersistedEvent[E, M]
  def wrapPersistedState(state: S): M & PersistedState[S, M]
  def wrapRecoveredState(state: S): M & RecoveredState[S, M]
  def unwrapPersistedEvents(message: M): Option[Seq[E]]
  def unwrapPersistedState(message: M): Option[S]
  def unwrapRecoveredState(message: M): Option[S]
}
```

- **技術的実装**:
  - Scala 3の交差型（Intersection Type）を活用した型安全な変換
  - パターンマッチングとアンチェック型キャストによる型安全な復元

#### 4. Result

ドメイン操作の結果を型安全にカプセル化するケースクラスです。

```scala
final case class Result[S, E](
  bankAccount: S,
  event: E,
)
```

- **技術的実装**:
  - 新しい状態とイベントを明示的にカプセル化
  - タプルと比較して意味的に明確で、コードの可読性を向上
  - ドメインロジックからの戻り値を標準化

#### 5. メッセージプロトコル関連トレイト

メッセージの基本構造を定義する一連のトレイトです。

```scala
trait PersistedEvent[E, M]
trait PersistedState[S, M]
trait RecoveredState[S, M] 
```

- **技術的実装**:
  - メッセージタイプを型安全に定義
  - 内部アクターとの通信用プロトコルを整理

## イベント永続化の内部実装

1. **イベント永続化の流れ**:
   - 集約アクターがPersistenceEffectorのpersistEvent/persistEventsメソッドを呼び出し
   - 内部子アクター（Untyped PersistentActor）がイベントを処理
   - アダプタを通じて永続化完了通知を受け取り
   - コールバック関数を実行して新しいBehaviorを返却

2. **状態復元の流れ**:
   - アクター起動時にPersistentActorが自動的に状態を復元
   - RecoveryCompletedシグナルで復元完了を検知
   - 復元された状態をメッセージアダプタを通じて親アクターに通知
   - onReady関数を呼び出して初期Behaviorを設定

## サンプル実装パターン

BankAccountAggregateの実装例から見る典型的な実装パターン:

1. **状態定義**:
   - 状態を表す列挙型（State）の定義
   - applyEventメソッドによるイベント適用ロジック実装

2. **集約アクター実装**:
   - 状態に応じたハンドラメソッドの分割（handleNotCreated, handleCreated）
   - ドメインロジック実行とイベント永続化の組み合わせ
   - 永続化後のBehavior返却による状態遷移

3. **PersistenceEffectorConfigの設定**:
   - 永続化ID、初期状態、イベント適用ロジックの定義
   - MessageConverterの統合によるメッセージ変換設定

## ドメインのモデル化パターン

Result型を活用したドメインモデリング:

1. **ドメイン操作の結果表現**:
   ```scala
   def add(amount: Money): Either[BankAccountError, Result[BankAccount, BankAccountEvent]]
   ```

2. **状態とイベントの明示的な関連付け**:
   ```scala
   Right(
     Result(
       copy(balance = balance + amount),
       BankAccountEvent.CashDeposited(bankAccountId, amount, Instant.now())
     )
   )
   ```

3. **Resultパターンの利点**:
   - 型安全性: 明示的な型で関連性を表現
   - 可読性向上: タプルよりも意図が明確
   - 保守性: パターンマッチングが明示的で変更に強い

## テスト戦略

PersistenceEffectorSpecから見るテスト戦略:

1. **インメモリジャーナルの活用**:
   - テスト用のインメモリジャーナルを設定
   - テスト間での状態分離のためのユニークID生成

2. **イベント永続化のテスト**:
   - 単一イベント永続化のテスト
   - 複数イベント永続化のテスト
   - 状態が正しく更新されることの検証

3. **状態復元のテスト**:
   - アクター停止・再起動による状態復元のテスト
   - 永続化されたイベントに基づく状態復元の検証

## 性能と拡張性の考慮

1. **スタッシングの活用**:
   - 永続化処理中のメッセージをスタッシュして後続処理を保証
   - スタッシュバッファサイズの上限設定（デフォルト32）

2. **非同期処理モデル**:
   - イベント永続化と状態更新を完全に非同期処理
   - メッセージアダプタを通じたコールバック駆動の設計

3. **拡張ポイント**:
   - カスタムのMessageConverter実装による変換ロジックの拡張
   - PersistenceEffectorConfigによる柔軟な設定調整

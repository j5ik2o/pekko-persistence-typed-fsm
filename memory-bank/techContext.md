# eff-sm-splitter 技術コンテキスト

## 技術概要

eff-sm-splitterは、Apache Pekkoを基盤としたイベントソーシングパターン実装支援ライブラリです。従来のPekko Persistence Typedの制約を解消し、より直感的なアクタープログラミングスタイルでイベントソーシングを実現します。具体的には、EventSourcedBehaviorを集約アクターの子アクターとして実装することで、通常のBehaviorベースのプログラミングパラダイムを維持しつつ永続化機能を提供します。

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

#### 1. Effector

永続化機能の中核となるトレイトおよびその実装です。

```scala
trait Effector[S, E, M] {
  def persist(event: E)(onPersisted: (S, E) => Behavior[M]): Behavior[M]
  def persistAll(events: Seq[E])(onPersisted: (S, Seq[E]) => Behavior[M]): Behavior[M]
}
```

- **技術的実装**:
  - `persist`メソッドは単一イベントの永続化を担当
  - `persistAll`メソッドは複数イベントのバッチ永続化を担当
  - 内部的にはメッセージアダプタを通じてEventSourcedBehaviorと通信
  - 永続化後のコールバック処理を型安全に実行

- **技術的特徴**:
  - コールバック関数がBehavior[M]を返すことで状態遷移と連動
  - メッセージスタッシングによる永続化中のメッセージ管理
  - 内部的な子アクターとしてEventSourcedBehaviorを実装

#### 2. EffectorConfig

Effectorの設定と動作を定義するための構成クラスです。

```scala
final case class EffectorConfig[S, E, M](
  persistenceId: String,
  initialState: S,
  applyEvent: (S, E) => S,
  wrapPersisted: (S, Seq[E]) => M,
  wrapRecovered: S => M,
  unwrapPersisted: M => Option[(S, Seq[E])],
  unwrapRecovered: M => Option[S]
)
```

- **技術的実装**:
  - 型パラメータS（状態）、E（イベント）、M（メッセージ）を持つジェネリックなケースクラス
  - `applyEvent`関数によるイベント適用ロジックの定義
  - WrappedISOとの統合によるボイラープレートコードの削減

#### 3. WrappedISO

状態、イベント、メッセージ間の変換を定義するトレイトです。

```scala
trait WrappedISO[S, E, M <: Matchable] {
  def wrapPersisted(state: S, events: Seq[E]): M & WrappedPersisted[S, E, M]
  def wrapRecovered(state: S): M & WrappedRecovered[S, M]
  def unwrapPersisted(message: M): Option[(S, Seq[E])]
  def unwrapRecovered(message: M): Option[S]
}
```

- **技術的実装**:
  - Scala 3の交差型（Intersection Type）を活用した型安全な変換
  - パターンマッチングとアンチェック型キャストによる型安全な復元

#### 4. WrappedBase関連トレイト

メッセージの基本構造を定義する一連のトレイトです。

```scala
sealed trait WrappedBase[M] { self: M => }

trait WrappedPersisted[S, E, M] extends WrappedBase[M] { self: M =>
  def state: S
  def events: Seq[E]
}

trait WrappedRecovered[S, M] extends WrappedBase[M] { self: M =>
  def state: S
}
```

- **技術的実装**:
  - self-type注釈を活用したトレイトミックスイン制約
  - ケースクラス/列挙型との組み合わせによる型安全なメッセージ定義

## イベント永続化の内部実装

1. **イベント永続化の流れ**:
   - 集約アクターがEffectorのpersist/persistAllメソッドを呼び出し
   - 内部子アクター（EventSourcedBehavior）がイベントを処理
   - アダプタを通じて永続化完了通知を受け取り
   - コールバック関数を実行して新しいBehaviorを返却

2. **状態復元の流れ**:
   - アクター起動時にEventSourcedBehaviorが自動的に状態を復元
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

3. **EffectorConfigの設定**:
   - 永続化ID、初期状態、イベント適用ロジックの定義
   - WrappedISOの統合によるメッセージ変換設定

## テスト戦略

EffectorSpecから見るテスト戦略:

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
   - カスタムのWrappedISO実装による変換ロジックの拡張
   - EffectorConfigによる柔軟な設定調整

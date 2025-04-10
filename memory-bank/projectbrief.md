# pekko-persistence-effector プロジェクト概要

## 基本情報

- プロジェクト名: eff-sm-splitter
- 言語: Scala 3.6.4
- ベースフレームワーク: Apache Pekko

## プロジェクト目的

pekko-persistence-effector は、Apache Pekkoを使用したイベントソーシングパターンの実装を補助するライブラリです。アクターモデルとイベントソーシングを組み合わせ、状態マシン（State Machine）の状態とイベントを分離して管理するための機能を提供します。

本ライブラリは特に、従来のPekko Persistence Typedの実装における以下の問題点を解決することを目的としています：

1. 従来のアクタープログラミングスタイルとの違いによる学習・実装の困難さ
2. 複雑な状態遷移実装時の保守性低下
3. コマンドハンドラでの状態遷移の制限によるドメインオブジェクト活用の難しさ

## 主要コンポーネント

### 1. PersistenceEffector

イベントの永続化機能を提供するコア部分です。EventSourcedBehaviorを集約アクターの子アクターとして実装することで、通常のアクタープログラミングスタイルを維持しつつイベントソーシングを実現します。

- `persistEvent`と`persistEvents`メソッドを通じて、イベントの永続化をサポート
- `persistSnapshot`メソッドでスナップショットの永続化をサポート
- Untyped PersistentActorを内部で使用（ドメインロジックの二重実行を避けるための設計）
- イベント永続化後のコールバック処理に対応
- インメモリモードをサポートし、段階的な実装が可能

### 2. PersistenceEffectorConfig

PersistenceEffectorの設定を定義するケースクラスです。

- 永続化ID、初期状態、イベント適用ロジックなどの基本設定
- メッセージ変換関数の設定
- `MessageConverter`との統合サポート

### 3. MessageConverter

状態(S)、イベント(E)、メッセージ(M)間の相互変換を定義するトレイトです。

- `wrapPersistedEvents`: イベントシーケンスをメッセージに変換する関数
- `wrapPersistedState`: 状態をメッセージに変換する関数
- `wrapRecoveredState`: 復元された状態をメッセージに変換する関数
- `unwrapPersistedEvents`/`unwrapPersistedState`/`unwrapRecoveredState`: メッセージから状態やイベントを抽出する関数

### 4. Result

ドメイン操作の結果をカプセル化するケースクラスです。

```scala
final case class Result[S, E](
  bankAccount: S,
  event: E,
)
```

- ドメインオブジェクトのメソッドから新しい状態とイベントを明示的に返すためのクラス
- タプルと比較して意味が明確で、コードの可読性と保守性が向上

## 使用例

テストコード(`EffectorSpec.scala`)からわかる基本的な使用方法:

1. 状態クラス(`TestState`)とイベント定義(`TestEvent`)を作成
2. メッセージタイプ(`TestMessage`)を定義し、`PersistedEvent`と`RecoveredState`を継承
3. `MessageConverter`の実装を作成し、変換関数を定義
4. `PersistenceEffectorConfig`で設定を行い、`PersistenceEffector.create`でアクターを初期化
5. イベントの永続化は`effector.persistEvent`または`effector.persistEvents`メソッドで実行

銀行口座集約(`BankAccountAggregate.scala`)の例も参照することで、複雑な状態遷移を通常のアクタープログラミングのスタイルで実装する方法を確認できます。

## メリット

- イベントソーシングパターンの実装を簡素化
- Apache Pekkoの機能を拡張して使いやすく抽象化
- 型安全な方法でイベントと状態の管理を実現
- アクターモデルとイベントソーシングの統合を容易に
- ステートマシンの分割と管理に特化した設計
- **従来のアクタープログラミングスタイルを維持可能**：通常のBehaviorベースのアクタープログラミングがそのまま適用可能
- **段階的な実装をサポート**：最初はインメモリモードで開発し、後から永続化対応へ移行できる
- **DDDとの親和性**：ドメインオブジェクトとのシームレスな統合が可能
- **複雑な状態遷移の保守性向上**：状態に応じたメソッド分割など、通常のアクタープログラミング技法が適用可能

## 適用シナリオ

以下のような場合には特に本ライブラリの活用をお勧めします：

- 最初から永続化を考慮しないでステップバイステップで実装する場合
- 状態遷移が複雑なドメインモデルを実装する場合
- ドメインロジックをアクターから分離したいDDD指向の設計を行う場合

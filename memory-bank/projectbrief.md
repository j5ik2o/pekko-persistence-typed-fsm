# eff-sm-splitter プロジェクト概要

## 基本情報

- プロジェクト名: eff-sm-splitter
- 言語: Scala 3.6.4
- ベースフレームワーク: Apache Pekko

## プロジェクト目的

eff-sm-splitterは、Apache Pekkoを使用したイベントソーシングパターンの実装を補助するライブラリです。アクターモデルとイベントソーシングを組み合わせ、状態マシン（State Machine）の状態とイベントを分離して管理するための機能を提供します。

## 主要コンポーネント

### 1. Effector

イベントの永続化機能を提供するコア部分です。

- `persist`と`persistAll`メソッドを通じて、イベントの永続化をサポート
- Apache Pekkoの`EventSourcedBehavior`を内部で使用
- イベント永続化後のコールバック処理に対応

### 2. EffectorConfig

Effectorの設定を定義するケースクラスです。

- 永続化ID、初期状態、イベント適用ロジックなどの基本設定
- メッセージ変換関数（ラッパー関数）の設定
- `WrappedISO`との統合サポート

### 3. WrappedISO

状態(S)、イベント(E)、メッセージ(M)間の相互変換を定義するトレイトです。

- `wrapPersisted`: 状態とイベントをメッセージに変換する関数
- `wrapRecovered`: 状態をメッセージに変換する関数
- `unwrapPersisted`/`unwrapRecovered`: メッセージから状態やイベントを抽出する関数

### 4. WrappedBase関連トレイト

メッセージの基本構造を定義する一連のトレイトです。

- `WrappedBase`: すべてのラップドメッセージの基底トレイト
- `WrappedPersisted`: 永続化されたイベントに関連するメッセージのトレイト
- `WrappedRecovered`: 復元された状態に関連するメッセージのトレイト
- self-typeを使用して実装クラスに制約を課している

### 5. WrappedSupportProtocol

メッセージタイプとラッパーの関係を定義する補助トレイトです。

## 使用例

テストコード(`EffectorSpec.scala`)からわかる基本的な使用方法:

1. 状態クラス(`TestState`)とイベント定義(`TestEvent`)を作成
2. メッセージタイプ(`TestMessage`)を定義し、`WrappedPersisted`と`WrappedRecovered`を継承
3. `WrappedISO`の実装を作成し、変換関数を定義
4. `EffectorConfig`で設定を行い、`Effector.create`でアクターを初期化
5. イベントの永続化は`effector.persist`または`effector.persistAll`メソッドで実行

## メリット

- イベントソーシングパターンの実装を簡素化
- Apache Pekkoの機能を拡張して使いやすく抽象化
- 型安全な方法でイベントと状態の管理を実現
- アクターモデルとイベントソーシングの統合を容易に
- ステートマシンの分割と管理に特化した設計

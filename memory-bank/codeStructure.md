# pekko-persistence-effector コード構造の設計思想

## コア構造: 関心の分離

pekko-persistence-effector のコード構造は、**関心の分離 (Separation of Concerns)** を中心的な原則として設計されています。主なコンポーネントとその役割分担は以下の通りです。

```mermaid
graph TD
    A[主アクター (Your Actor)] -- 永続化依頼 (イベント) --> PE(PersistenceEffector);
    PE -- 永続化完了/リカバリー完了 --> MC(MessageConverter);
    MC -- アクター向けメッセージ --> A;
    PE -- 設定読み込み --> CONF(PersistenceEffectorConfig);
    CONF -- 変換ロジック指定 --> MC;
    A -- ドメインロジック実行 --> DM(ドメインモデル);
    DM -- 結果 (新状態, イベント) --> A;

    subgraph "永続化レイヤー"
        PE
        CONF
        MC
        PEI(PersistenceEffector 実装);
    end

    subgraph "アプリケーションレイヤー"
        A
        DM
    end

    PE --- PEI;
```

- **主アクター (Your Actor):** ビジネスロジックの実行、状態管理、メッセージハンドリングを担当します。永続化が必要な場合、イベントを `PersistenceEffector` に依頼します。**通常の Pekko Actor スタイル (`Behavior` ベース) で実装されます。**
- **ドメインモデル (Domain Model):** 純粋なビジネスロジック（状態遷移ルール、検証など）をカプセル化します。アクターから独立しており、テスト容易性が高いです。
- **PersistenceEffector (Trait):** イベントとスナップショットの永続化操作のインターフェースを定義します。主アクターはこのインターフェースを通じて永続化を依頼します。
- **PersistenceEffector 実装 (InMemory / Default):** `PersistenceEffector` トレイトの具体的な実装です。ストラテジーパターンにより、永続化戦略（インメモリか、実際のDBか）を切り替え可能にします。
    - `InMemoryEffector`: 開発・テスト用のインメモリ実装。
    - `DefaultPersistenceEffector`: Pekko Persistence を利用した実際の永続化実装。内部で PersistentActor を利用しますが、主アクターからは隠蔽されています。
- **PersistenceEffectorConfig:** `PersistenceEffector` の動作に必要な設定（永続化ID、初期状態、イベント適用関数、`MessageConverter` など）を提供します。
- **MessageConverter:** 主アクターのメッセージ型 (`M`) と、永続化レイヤーが扱う状態 (`S`)・イベント (`E`) との間の型安全な変換を担当します。これにより、主アクターと永続化レイヤーが互いの内部詳細に依存しないようにします。

**なぜこの構造なのか？**
この分離により、以下の利点が生まれます。
- **主アクターのシンプル化:** 永続化の複雑さを `PersistenceEffector` に委譲することで、主アクターはビジネスロジックに集中できます。
- **従来スタイルの維持:** 主アクターは `EventSourcedBehavior` のような特殊な DSL を使う必要がなく、慣れ親しんだ `Behavior` ベースのスタイルで実装できます。
- **テスト容易性:** ドメインモデルは単体テストが容易です。`InMemoryEffector` を使えば、永続化を含むアクターの振る舞いも高速にテストできます。
- **柔軟性:** `PersistenceMode` の切り替えや、`SnapshotCriteria`/`RetentionCriteria` による戦略のカスタマイズが容易です。

## 主要コンポーネントの役割と設計意図

| コンポーネント                 | 役割                                                                 | 設計意図・なぜ必要か？                                                                                                                               |
| :----------------------------- | :------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PersistenceEffector` (Trait)  | イベント/スナップショット永続化の**インターフェース**                  | 主アクターが具体的な永続化実装（インメモリ/DB）を意識せずに済むように、操作を抽象化するため。                                                               |
| `DefaultPersistenceEffector` | **実際の永続化** (Pekko Persistence利用)                             | 本番環境での永続化を実現するため。内部の PersistentActor 利用は、安定した基盤を活用しつつ、主アクターのロジック二重実行を防ぐため。                       |
| `InMemoryEffector`           | **インメモリ永続化** (開発/テスト用)                                 | データベース設定なしで迅速な開発・テストを可能にするため。段階的な永続化導入をサポートするため。                                                         |
| `PersistenceEffectorConfig`  | Effector の**設定情報**を集約                                        | Effector の動作に必要なパラメータ（ID, 初期状態, イベント適用方法, 変換方法など）を一元管理し、生成を容易にするため。                                       |
| `MessageConverter`           | **型変換** (状態/イベント/メッセージ間)                              | 主アクターのメッセージ型と永続化レイヤーの内部型を分離し、疎結合にするため。Scala 3 の交差型などを活用し、型安全な変換を実現するため。                   |
| `MessageWrapper` (関連Trait) | `MessageConverter` が返す**メッセージの型情報**を付与 (マーカー)       | `PersistenceEffector` からの通知メッセージ（例: 永続化完了）の種類と、それに含まれるデータ（例: イベント）を型安全に識別できるようにするため。             |
| `SnapshotCriteria`           | **スナップショット取得戦略**の定義                                   | いつスナップショットを取るか、という戦略を外部から注入可能にするため（ストラテジーパターン）。                                                           |
| `RetentionCriteria`          | **イベント/スナップショット保持戦略**の定義                          | 古いデータをどのように削除するか、という戦略を外部から注入可能にするため（ストラテジーパターン）。                                                       |

## 実装例 (BankAccountAggregate) の構造的側面

`BankAccountAggregate` のサンプル実装は、この構造を以下のように活用しています。

- **アクター定義:** 通常の `Behaviors.setup` を使用し、その中で `PersistenceEffector.create` を呼び出して子アクターとして `PersistenceEffector` (InMemory または Default) を生成します。
- **状態管理:** アクターの状態 (`State` enum: `NotCreated`, `Created`) を定義し、現在の状態に応じて異なる `Behavior` (`handleNotCreated`, `handleCreated`) を返します。
- **コマンド処理:**
    - コマンドを受け取ると、対応する `Behavior` 内で処理します。
    - ドメインロジック（例: `bankAccount.add`）を実行し、`Result(newState, event)` を受け取ります。
    - 新しい状態 (`newState`) をアクターの次の `Behavior` に反映させます。
    - イベント (`event`) を `effector.persistEvent` に渡して永続化を依頼します。
    - 永続化完了のコールバック内で、応答メッセージを送信したり、さらに `Behavior` を変更したりします。
- **リカバリー:** アクター起動時、`PersistenceEffector.create` のコールバックに、リカバリーされた状態 (`initialState`) が渡されます。これを使って初期の `Behavior` を決定します。
- **メッセージ変換:** `BankAccountCommand` enum 内に `MessageConverter` の実装を含め、`PersistenceEffector` からの内部通知（`PersistedEvent`, `RecoveredState` など）を `BankAccountCommand` のプライベートなケースクラスにマッピングします。

このように、`BankAccountAggregate` はビジネスロジックと状態遷移に集中し、永続化の詳細は `PersistenceEffector` に委任するという、関心の分離が実現されています。

## テスト戦略との関連

このコード構造はテスト容易性に貢献します。
- ドメインモデルはアクターから独立しているため、単体テストが容易です。
- `InMemoryEffector` を使用することで、永続化層をモックすることなく、イベント永続化やリカバリーを含むアクターの振る舞いを `ActorTestKit` でテストできます。
詳細は `testPatterns.md` を参照してください。

## まとめ

pekko-persistence-effector のコード構造は、**関心の分離**を徹底し、**従来のアクタープログラミングスタイル**を維持しながら**イベントソーシング**を実現することを目指しています。`PersistenceEffector` という抽象化レイヤーと、`InMemoryEffector` による段階的実装のサポートが、その中心的な特徴です。この構造により、開発者はドメインロジックに集中でき、テスト容易性と保守性の高いアプリケーションを構築することが可能になります。

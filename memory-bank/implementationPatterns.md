# pekko-persistence-effector 実装パターンとドメイン駆動設計（DDD）

本ドキュメントでは、pekko-persistence-effector を利用する際に推奨される実装パターンと、それがドメイン駆動設計（DDD）の原則とどのように連携するかを、サンプル実装（BankAccount）を例に解説します。コードの断片ではなく、その背後にある設計思想や「なぜ」そのパターンが有効なのかに焦点を当てます。

## 実装パターンの設計思想

pekko-persistence-effector を用いた実装では、以下の設計思想に基づいたパターンが推奨されます。

1.  **ドメインモデルとアクターの分離:**
    *   **思想:** ドメイン固有のビジネスロジック（状態遷移、検証ルールなど）は、アクターの外部にある純粋なドメインモデル（ケースクラス、オブジェクトなど）にカプセル化します。アクターは、メッセージの受信、ドメインモデルへの処理委譲、`PersistenceEffector` への永続化依頼、そして応答の調整役（コーディネーター）に徹します。
    *   **利点:** ドメインロジックをアクターのライフサイクルやメッセージングの詳細から切り離せるため、単体テストが容易になり、ロジックの再利用性も向上します。アクターのコードはシンプルに保たれます。

2.  **Result パターンによる明確な結果表現:**
    *   **思想:** ドメインモデルの操作（コマンド処理）の結果として、単に新しい状態を返すだけでなく、「新しい状態」と「その状態遷移を引き起こしたイベント」のペアを明示的なデータ構造（例: `Result(newState, event)`）で返します。
    *   **利点:** ドメインロジックの実行と、その結果としてのイベント永続化という副作用を明確に分離できます。アクターは `Result` を受け取り、そこに含まれるイベントを `PersistenceEffector` に渡して永続化を依頼します。タプル `(State, Event)` よりも意図が明確で、将来的な拡張（例: 結果に追加情報を含める）にも対応しやすくなります。

3.  **状態に応じた振る舞いの分割:**
    *   **思想:** アクターの状態を `enum` などで型安全に定義し、状態ごとに異なるメッセージハンドリングロジック（`Behavior`）を定義します。例えば、「口座未開設状態 (`NotCreated`)」と「口座開設済み状態 (`Created`)」で受け付けるコマンドや処理内容が異なる場合、それぞれに対応する `handleNotCreated` や `handleCreated` といった `Behavior` を用意します。
    *   **利点:** アクターの振る舞いが状態によって大きく変わる場合に、単一の巨大な `receive` ブロックよりもコードの見通しが格段に良くなります。各状態で有効な操作が型レベルで明確になり、不正な状態遷移を防ぎやすくなります。

4.  **イベントソーシングの原則適用:**
    *   **思想:** システムの状態は、発生したイベントのシーケンスを適用することで導出される、というイベントソーシングの基本原則に従います。コマンドが処理されるとイベントが発生し、そのイベントが永続化され、状態が更新されます。
    *   **利点:** 変更履歴がイベントとして保存されるため、監査証跡の取得や、過去の特定時点の状態の再現が可能です。状態の更新ロジック（`applyEvent`）を一箇所に集約できます。

5.  **段階的な永続化導入 (モード切替):**
    *   **思想:** 開発初期段階では `InMemoryEffector` を使用し、永続化の詳細（データベース設定など）を意識せずにドメインロジックとアクターの振る舞いの実装に集中できます。後に必要に応じて `DefaultPersistenceEffector` に切り替えることで、実際の永続化バックエンドを導入します。
    *   **利点:** 開発の初期段階でのセットアップの手間を省き、迅速なプロトタイピングや TDD を可能にします。永続化要件が未確定な場合や、段階的に機能をリリースする場合にも有効です。テスト実行も高速になります。

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

## ドメイン駆動設計 (DDD) との連携

pekko-persistence-effector の設計思想は、DDD のプラクティスと自然に連携します。

- **集約 (Aggregate):** アクター（例: `BankAccountAggregate`）が集約ルートの役割を果たします。集約は一貫性の境界となり、外部からのアクセスポイントを提供します。アクターの状態 (`State` enum) が集約のライフサイクル（例: 未作成、作成済み）を表現し、`PersistenceEffector` が集約の状態（イベント）の永続化を担当します。ドメインロジックをドメインモデルに分離するパターンは、集約ルートの責務を明確にする上で役立ちます。
- **エンティティ (Entity) と 値オブジェクト (Value Object):** ドメインモデルは、識別子を持つエンティティ（例: `BankAccount`）と、属性で識別される値オブジェクト（例: `Money`）で構成されます。これらは不変 (immutable) として設計することが推奨され、pekko-persistence-effector は状態更新時に新しいインスタンスを生成するこのスタイルとよく適合します。
- **ドメインイベント (Domain Event):** ドメインモデルの操作結果として `Result` に含まれるイベント（例: `BankAccountEvent`）が、DDD におけるドメインイベントに相当します。これらは過去に起こった事実を表し、状態変更の根拠となります。`enum` で型安全に定義することが推奨されます。
- **コマンド (Command):** アクターが受信するメッセージ（例: `BankAccountCommand`）がコマンドに相当します。システムに対する意図を表現し、通常は対象となる集約の ID と、応答を返すための `replyTo` を含みます。
- **リポジトリ (Repository):** `PersistenceEffector` は、イベントの永続化と読み込み（リカバリー）を抽象化する点で、イベントリポジトリのような役割を果たします。主アクターは永続化ストレージの詳細を意識することなく、`PersistenceEffector` を通じてイベントの保存と、起動時の状態復元（イベントの再生）を行います。

## ユースケース実装パターン解説

サンプル実装（BankAccount）に見られる典型的なユースケースの処理フローとその背後にある考え方を解説します。

1.  **集約の作成 (Create):**
    *   **フロー:** `NotCreated` 状態のアクターが `Create` コマンドを受信 → ドメインモデル (`BankAccount.create`) を呼び出し、初期状態と `Created` イベントを含む `Result` を取得 → `PersistenceEffector` に `Created` イベントの永続化を依頼 → 永続化完了のコールバック内で、リクエスト元に成功応答 (`CreateReply.Succeeded`) を送信し、アクターの `Behavior` を `handleCreated` に遷移させる。
    *   **なぜ？:**
        *   初期状態 (`NotCreated`) では `Create` 以外のコマンドを受け付けないようにし、不正な操作を防ぎます。
        *   イベントが確実に永続化された後で、応答と状態遷移を行うことで、システムの整合性を保ちます（永続化失敗時に中途半端な状態になるのを防ぐ）。

2.  **状態変更操作 (Update - 例: DepositCash):**
    *   **フロー:** `Created` 状態のアクターが `DepositCash` コマンドを受信 → 保持しているドメインモデル (`state.bankAccount`) の `add` メソッドを呼び出し → バリデーション（例: 上限チェック）の結果を `Either` で受け取る →
        *   **失敗時 (Left):** リクエスト元に失敗応答 (`DepositCashReply.Failed`) を送信し、`Behaviors.same` で状態は変更しない。
        *   **成功時 (Right):** `Result` から新しい状態 (`newBankAccount`) と `CashDeposited` イベントを取得 → `PersistenceEffector` にイベントの永続化を依頼 → 永続化完了のコールバック内で、リクエスト元に成功応答 (`DepositCashReply.Succeeded`) を送信し、アクターの `Behavior` を新しい状態 (`state.copy(bankAccount = newBankAccount)`) を持つ `handleCreated` に遷移させる。
    *   **なぜ？:**
        *   ドメインモデル内でビジネスルール（バリデーション）をチェックし、結果を `Either` で明確に返します。
        *   失敗時はイベントを発生させず、状態も変更しません。応答だけを返します。
        *   成功時のみイベントを永続化し、永続化が完了してから応答と状態遷移を行います。これにより、イベントログとアクターの状態の一貫性を保証します。

3.  **照会操作 (Query - 例: GetBalance):**
    *   **フロー:** `Created` 状態のアクターが `GetBalance` コマンドを受信 → 現在のアクターの状態 (`state.bankAccount.balance`) から残高を取得 → リクエスト元に残高を含む成功応答 (`GetBalanceReply.Succeeded`) を送信 → `Behaviors.same` で状態は変更しない。
    *   **なぜ？:**
        *   状態を読み取るだけの操作なので、イベントを発生させる必要も、永続化する必要もありません。
        *   現在の状態から直接応答を生成し、即座に返します。状態遷移も伴いません。

4.  **集約の停止 (Stop):**
    *   **フロー:** アクターが `Stop` コマンドを受信 → リクエスト元に成功応答 (`StopReply.Succeeded`) を送信 → `Behaviors.stopped` を返してアクターを停止させる。
    *   **なぜ？:**
        *   アクターが停止する前に、リクエスト元に停止処理が受け付けられたことを通知します。
        *   `Behaviors.stopped` により、アクターシステムが適切にアクターをシャットダウンします。

## テストパターンとの関連

これらの実装パターンは、テスト容易性を考慮して設計されています。

- **ドメインモデルの単体テスト:** アクターから分離されたドメインモデルは、Pekko Actor TestKit を使わずに、通常の単体テストフレームワーク（ScalaTest など）で容易にテストできます。
- **アクターの統合テスト:** `ActorTestKit` と `TestProbe` を使用して、アクターへのコマンド送信、期待される応答メッセージの受信、そして状態遷移（必要であれば `InMemoryEffector` の `getState` を利用）を検証できます。
- **InMemory モードの活用:** `InMemoryEffector` を使うことで、データベースなどの外部依存なしに、イベント永続化とリカバリーを含むアクターの振る舞いを高速にテストできます。

テスト戦略と具体的なテスト手法の詳細については、`testPatterns.md` を参照してください。

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

## 実装パターンがもたらす価値

これらの実装パターンを採用することで、以下の価値が得られます。

- **高いテスト容易性:** ドメインロジックがアクターから分離されているため、個別にテストが容易です。`InMemoryEffector` により、永続化を含むアクターの振る舞いも高速にテストできます。
- **保守性と拡張性の向上:** 関心が明確に分離され、状態遷移が明示的に管理されるため、コードの理解や変更が容易になります。新しい状態やコマンドの追加も、影響範囲を限定しやすくなります。
- **ドメインへの集中:** 開発者は、永続化の低レベルな詳細ではなく、ドメインの本質的な複雑さに集中できます。
- **コードの明確性:** `Result` パターンや状態ごとのハンドラ分割により、コードの意図が明確になり、可読性が向上します。`Either` を用いたエラーハンドリングも、失敗ケースの処理を明確にします。
- **柔軟な開発プロセス:** 段階的な永続化導入により、開発初期のオーバーヘッドを削減し、アジャイルな開発プロセスを支援します。

## まとめ: なぜこのアプローチなのか？

pekko-persistence-effector で推奨される実装パターンは、イベントソーシングとアクターモデル、そしてドメイン駆動設計の原則を組み合わせ、それぞれの利点を活かすことを目指しています。

- **関心の分離:** ドメインロジック、アクターの調整役、イベント永続化という異なる関心を明確に分離します。
- **型安全性:** Scala の型システムを活用し、状態、イベント、コマンド、メッセージ間の関係を安全に扱います。
- **明示性:** 状態遷移やドメイン操作の結果をコード上で明示的に表現することで、暗黙的な動作や予期せぬ副作用を減らします。
- **テスト容易性:** 設計の各段階でテストのしやすさを考慮に入れています。

このアプローチは、特に以下のような場合に有効です。
- ドメインロジックが複雑で、アクター内部にすべてを詰め込むと見通しが悪くなる場合。
- ドメインモデルのテスト容易性を重視する場合。
- Pekko Persistence Typed の DSL よりも、従来のアクタープログラミングスタイルを好む場合。
- 永続化要件が後から決まる、または段階的に導入したい場合。

「状態に応じたハンドラ分割」や「Result パターン」といった具体的なテクニックは、この設計思想を実現するための手段であり、これらを組み合わせることで、複雑なビジネスロジックを持つアプリケーションにおいても、保守性と拡張性の高いイベントソーシング実装を構築することが可能になります。

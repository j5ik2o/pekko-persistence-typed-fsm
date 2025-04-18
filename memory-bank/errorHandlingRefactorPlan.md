# エラーハンドリング リファクタリング計画: 呼び出し側リトライ

この計画は、永続化操作のエラーハンドリング機構をリファクタリングし、タイムアウトを伴う呼び出し側リトライ戦略を実装するためのステップを概説します。

**目標:** `PersistenceEffector` による監視のもと、`DefaultPersistenceEffector` にリトライを実装することで、一時的な永続化失敗に対する回復力を向上させます。

**アプローチ:** `DefaultPersistenceEffector` は `PersistenceStoreActor` からの応答を監視します。応答がタイムアウト内に到着しない場合、設定された上限までリクエストをリトライします。`PersistenceStoreActor` は、自身の終了につながる回復不能なエラーに対して、リスタート（バックオフ付き）戦略で監視されます。

## サブタスク

### 1. `PersistenceStoreActor` の単純化と監視設定

-   [ ] **`PersistenceStoreActor.scala` のレビュー:** `onPersistFailure` がアクターの終了につながることを確認します（例: 例外スローまたは `context.stop` 呼び出し）。もし存在すれば、既存の内部リトライロジックを削除します。
-   [ ] **`PersistenceEffector.scala` の修正:**
    -   [ ] `spawnEventStoreActor` 内で `PersistenceStoreActor` を生成する際に `SupervisorStrategy.restartWithBackoff` を適用します。
    -   [ ] 適切なバックオフパラメータ（`minBackoff`, `maxBackoff`, `randomFactor`）を定義します。これらを後で設定可能にすることを検討します。
-   [ ] **テスト:** シミュレートされた失敗の後、`PersistenceStoreActor` が再起動することを確認します。

### 2. `DefaultPersistenceEffector` へのタイマー導入

-   [ ] **`PersistenceEffector.scala` の修正:** `DefaultPersistenceEffector` がタイマーを使用できるコンテキスト内で生成されることを確認します（例: `Behaviors.setup` と `Behaviors.withTimers` を使用）。これは `Behaviors.withStash` によって既に満たされているかもしれませんが、確認します。
-   [ ] **`DefaultPersistenceEffector.scala` の修正:** もしまだなければ、そのセットアップに `Behaviors.withTimers` を追加します。

### 3. タイムアウト監視の実装

-   [ ] **内部メッセージの定義:**
    -   [ ] `DefaultPersistenceEffector.scala` 内で、`PersistenceStoreActor` に送信されるリクエスト（一意なリクエストIDと元の `replyTo` ref を含む）のためのメッセージラッパーを定義します。
    -   [ ] タイムアウトメッセージ（例: `RequestTimeout(requestId: UUID)`）を定義します。
-   [ ] **`DefaultPersistenceEffector.scala` の修正:**
    -   [ ] `PersistenceStoreActor` にリクエスト（`PersistSingleEvent` など）を送信する際:
        -   [ ] 一意なリクエストIDを生成します。
        -   [ ] リクエスト詳細（イベント、元の `replyTo`、リトライ回数）をIDに関連付けて保存します（例: `Map` 内）。
        -   [ ] `context.scheduleOnce` を使用して、定義されたタイムアウト時間でタイマーを開始し、リクエストIDを含む `RequestTimeout` メッセージを `self` に送信します。
    -   [ ] `adapter` 経由で成功応答（`PersistSingleEventSucceeded` など）を受信した際:
        -   [ ] 元のリクエストIDを抽出します（応答とリクエストを関連付けるために、成功メッセージまたはアダプターロジックの若干の修正が必要になる場合があります）。
        -   [ ] リクエストIDを使用して対応するタイマーをキャンセルします。
        -   [ ] 保留中のマップからリクエスト詳細を削除します。
        -   [ ] 元の `replyTo` に成功応答を転送します。
-   [ ] **テスト:** タイマーが正しく設定され、成功時にキャンセルされることを確認します。

### 4. リトライロジックの実装

-   [ ] **`DefaultPersistenceEffector.scala` の修正:**
    -   [ ] `RequestTimeout` メッセージのハンドリングを実装します。
    -   [ ] `RequestTimeout` を受信した際:
        -   [ ] `requestId` を使用して保留中のリクエスト詳細を検索します。
        -   [ ] そのリクエストのリトライ回数をインクリメントします。
        -   [ ] **リトライ上限の確認:** リトライ回数が上限内の場合:
            -   [ ] 元のリクエストを `PersistenceStoreActor` に再送します。
            -   [ ] このリクエストIDのタイムアウトタイマーを再開します。
            -   [ ] 保存されているリトライ回数を更新します。
        -   [ ] **リトライ上限超過の処理:** リトライ回数が上限を超えた場合、最終的な失敗処理ロジック（ステップ5参照）をトリガーします。
-   [ ] **設定:** リトライ上限を定義します（例: `PersistenceEffectorConfig` 内）。
-   [ ] **テスト:** タイムアウト時にリクエストがリトライされることを確認します。

### 5. 最終的な失敗処理の実装 (方針決定: 呼び出し元に通知)

-   [x] **決定:** リトライ上限を超えた場合の振る舞いは、**選択肢B: 呼び出し元に通知** とします。
    -   エラーをログに記録します。
    -   元のリクエストの `replyTo` に対して、永続化が最終的に失敗したことを示す専用のメッセージ（例: `PersistFailedAfterRetries(originalCommand, cause)`）を送信します。
-   [ ] **実装:**
    -   [ ] リトライ上限を超えた場合の `RequestTimeout` ハンドラーに、上記のログ記録と失敗メッセージ送信ロジックを実装します。
    -   [ ] 必要な失敗メッセージ型（例: `PersistFailedAfterRetries`）を定義します。このメッセージには、元のコマンドや失敗原因を含めることを検討します。
    -   [ ] 必要に応じて `MessageConverter` を更新し、この新しい失敗メッセージをラップ/アンラップできるようにします。

### 6. 状態管理と Stash の調整

-   [ ] **`DefaultPersistenceEffector.scala` のレビュー:**
    -   [ ] 保留中のリクエストを保持するマップ（`Map[RequestId, PendingRequestDetails]`）が正しく管理されていること（送信時に追加、成功時または最終失敗時に削除）を確認します。
    -   [ ] `StashBuffer` の使用法を確認します。応答待ち中またはリトライバックオフ中に受信したメッセージが、順序を維持しメッセージ損失を防ぐために適切に Stash および Unstash されることを確認します。複数の同時リクエスト/リトライ間の潜在的な相互作用を考慮します。
-   [ ] **テスト:** 同時負荷およびリトライシナリオ下での状態の一貫性とメッセージハンドリングを確認します。

### 7. 冪等性の確認 (レビュー)

-   [ ] **`BankAccountAggregate.scala` (および他の潜在的な利用者) のレビュー:**
    -   [ ] `applyEvent` ロジックを分析します。同じイベントを複数回適用しても最終的な状態が同じになることを確認します。
    -   [ ] 永続化成功コールバックの*後*に実行されるロジック（例: リプライ送信）を分析します。最初のコマンドが成功したが応答が失われた後にリトライが発生した場合に、これらのアクションを複数回実行しても安全であることを確認します。
-   [ ] **文書化:** `PersistenceEffector` の利用者に対する冪等性の要件に関する注記またはドキュメントを追加します。

### 8. テスト

-   [ ] **単体テストの追加:** `DefaultPersistenceEffector` の単体テストを作成し、以下に焦点を当てます:
    -   [ ] タイムアウト検出。
    -   [ ] 正しいリトライ試行。
    -   [ ] リトライ上限の処理。
    -   [ ] 最終的な失敗処理（ステップ5の決定に基づく）。
    -   [ ] 成功時のタイマーキャンセル。
    -   [ ] リトライ中の状態管理。
-   [ ] **統合テストの追加:** `PersistenceEffector` と `PersistenceStoreActor` を含む統合テストを作成または修正し、以下をシミュレートします:
    -   [ ] 一時的な永続化失敗（`onPersistFailure` が再起動をトリガー）。
    -   [ ] 応答の損失によるタイムアウトとリトライ。
    -   [ ] リトライ上限に達するシナリオ。
-   [ ] **既存テストのレビュー:** 既存のテストがまだ有効であるか確認し、新しい振る舞いに合わせて更新します。

## 設定パラメータ

-   [ ] 永続化リクエストのタイムアウト時間を定義します。
-   [ ] 最大リトライ回数を定義します。
-   [ ] `PersistenceStoreActor` 再起動のためのバックオフパラメータ（`minBackoff`, `maxBackoff`, `randomFactor`）を定義します。
-   [ ] これらの設定をどこに配置するか決定します（例: `PersistenceEffectorConfig`, HOCON）。

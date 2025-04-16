# pekko-persistence-effector テスト戦略と実装パターン

## テスト戦略の概要

pekko-persistence-effectorは複数層のテスト戦略を採用しており、主に以下のレベルでテストを行っています：

1. **単体テスト**: コンポーネントごとの機能検証
2. **統合テスト**: アクター間の相互作用のテスト
3. **モード横断テスト**: InMemoryモードとPersistedモードの双方での動作検証
4. **サンプル実装テスト**: 実際のユースケース（BankAccount）によるテスト

## テスト基盤

### PersistenceEffectorTestBase

テストの基盤となる抽象クラスとして`PersistenceEffectorTestBase`が提供されています。このクラスは：

- 永続化モードに依存しない共通のテストケースを定義
- サブクラスで永続化モードを指定できる抽象メソッドを提供
- スナップショットテストの有効/無効を制御するフラグを提供

```scala
abstract class PersistenceEffectorTestBase
  extends ScalaTestWithActorTestKit(TestConfig.config)
  with AnyWordSpecLike
  with Matchers
  with Eventually
  with BeforeAndAfterAll
  with OptionValues {

  // サブクラスで実装するメソッド - テスト対象のPersistenceMode
  def persistenceMode: PersistenceMode
  
  // スナップショットテストを実行するかどうか
  def runSnapshotTests: Boolean = true
  
  // ...
}
```

### テスト実装のバリエーション

- **InMemoryEffectorSpec**: インメモリモード特有のテスト
- **PersistedEffectorSpec**: 実際の永続化モードのテスト
- **SnapshotCriteriaSpec**: スナップショット戦略のテスト
- **RetentionCriteriaSpec**: 保持ポリシーのテスト

## 主要なテストケース

### 1. 状態の復元テスト

```scala
"properly handle state recovery" in {
  val persistenceId = s"test-recovery-${java.util.UUID.randomUUID()}"
  val initialState = TestState()
  
  // ... 設定 ...
  
  val behavior = spawn(Behaviors.setup[TestMessage] { context =>
    PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
      case (state, effector) =>
        recoveredEvents += TestMessage.StateRecovered(state)
        // ... Behavior ...
    }(using context)
  })
  
  eventually {
    recoveredEvents.size shouldBe 1
    recoveredEvents.head shouldBe TestMessage.StateRecovered(initialState)
  }
}
```

このテストでは、アクター初期化時に状態が正しく復元されることを検証しています。

### 2. イベント永続化テスト

```scala
"successfully persist single event" in {
  // ... 設定 ...
  
  val behavior = spawn(Behaviors.setup[TestMessage] { context =>
    PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
      case (state, effector) =>
        effector.persistEvent(event) { _ =>
          events += TestMessage.EventPersisted(Seq(event))
          Behaviors.stopped
        }
    }(using context)
  })
  
  eventually {
    events.size shouldBe 1
    val TestMessage.EventPersisted(evts) = events.head: @unchecked
    evts should contain(event)
  }
}
```

このテストでは、単一イベントが正しく永続化されることを検証しています。

### 3. アクターの停止と再起動時の状態復元テスト

```scala
"restore state after actor is stopped and restarted with the same id" in {
  // ... 1回目のアクター実行 ...
  
  // 2回目のアクターを実行（同じID）
  val behavior2 = spawn(Behaviors.setup[TestMessage] { context =>
    PersistenceEffector.create[TestState, TestEvent, TestMessage](config2) {
      case (state, effector) =>
        // 復元された状態を記録
        secondRunRecoveredState += state
        // ...
    }(using context)
  })
  
  // 状態が正しく復元されていることを確認
  eventually {
    secondRunRecoveredState.size shouldBe 1
    val recoveredState = secondRunRecoveredState.head
    // 1回目のアクターで永続化したイベントが適用された状態が復元されていることを確認
    recoveredState.values should contain.allOf("event1", "42")
  }
}
```

このテストは、アクターが停止して再起動した後でも、永続化されたイベントが適用された状態が正しく復元されることを検証します。

### 4. スナップショット関連テスト

```scala
"persist event with state" in {
  assume(runSnapshotTests, "Snapshot test is disabled")
  // ... 設定 ...
  
  val behavior = spawn(Behaviors.setup[TestMessage] { context =>
    PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
      case (state, effector) =>
        // persistEventWithStateを使用
        effector.persistEventWithState(event, newState, force = false) { _ =>
          // ... 処理 ...
        }
    }(using context)
  })
  
  eventually {
    // ... 検証 ...
    snapshots.size shouldBe 1
    val TestMessage.SnapshotPersisted(state) = snapshots.head: @unchecked
    state.values should contain("event-with-state")
  }
}
```

このテストでは、イベントと状態を同時に永続化し、スナップショット戦略に基づいてスナップショットが作成されることを検証しています。

### 5. 保持ポリシーテスト

```scala
"apply retention policy when taking snapshots" in {
  assume(runSnapshotTests, "Snapshot test is disabled")
  // ... 設定 ...
  
  // スナップショットを2つおきに作成し、最新の2つだけ保持する設定
  val config = PersistenceEffectorConfig[TestState, TestEvent, TestMessage](
    // ... 
    snapshotCriteria = Some(SnapshotCriteria.every[TestState, TestEvent](2)),
    retentionCriteria = Some(RetentionCriteria.snapshotEvery(2, 2))
  )
  
  // ... テスト実行 ...
  
  eventually {
    // 削除メッセージの検証
    deletedSnapshotMessages.nonEmpty shouldBe true
    
    // シーケンス番号2のスナップショットが削除対象になっていることを確認
    val seqNr = deletedSnapshotMessages.collectFirst {
      case TestMessage.SnapshotsDeleted(maxSeqNr) => maxSeqNr
    }.getOrElse(0L)
    
    seqNr should be > 0L
  }
}
```

このテストでは、スナップショット保持ポリシーに基づいて古いスナップショットが削除されることを検証しています。

## InMemoryモード特有のテスト

InMemoryモードでは、実際の永続化処理を行わないため、いくつかの追加検証が必要です：

```scala
"provide access to current state via getState" in {
  // ... 設定 ...
  
  val behavior = spawn(Behaviors.setup[TestMessage] { context =>
    PersistenceEffector.create[TestState, TestEvent, TestMessage](config) {
      case (state, effector) =>
        // 型キャストで InMemoryEffector を取得
        val inMemoryEffector =
          effector.asInstanceOf[InMemoryEffector[TestState, TestEvent, TestMessage]]
        effectorRef = Some(inMemoryEffector)
        // ... 処理 ...
    }(using context)
  })
  
  eventually {
    // InMemoryEffectorのgetStateメソッドで状態が取得できることを確認
    effectorRef.isDefined shouldBe true
    // ... 状態の検証 ...
  }
}
```

また、`InMemoryEffector`でイベント適用ロジックが二重実行されないことを確認するテストも追加されています：

```scala
"not execute applyEvent twice when persistEvent is called" in {
  // applyEventの呼び出し回数をカウントするための変数
  var applyEventCount = 0
  
  // 呼び出し回数をカウントするapplyEvent関数
  val countingApplyEvent = (state: TestState, event: TestEvent) => {
    applyEventCount += 1
    state.applyEvent(event)
  }
  
  // ... テスト実行 ...
  
  // applyEventが1回だけ呼ばれることを確認（手動更新時のみ）
  applyEventCount shouldBe 1
}
```

## 実装パターン

テストから見えてくる実装パターンには、以下のようなものがあります：

### 1. テスト識別子の一意性確保

テストごとに一意のID（UUID）を使用することで、テスト間の干渉を防いでいます：

```scala
val persistenceId = s"test-persist-single-${java.util.UUID.randomUUID()}"
```

### 2. Eventually パターン

アクターの非同期処理を扱うため、`eventually`ブロックを使用して条件が満たされるまで待機します：

```scala
eventually {
  recoveredEvents.size shouldBe 1
  // ... 検証 ...
}
```

### 3. テスト前提条件のスキップ

特定の条件が満たされない場合にテストをスキップする`assume`を使用：

```scala
assume(runSnapshotTests, "Snapshot test is disabled")
```

### 4. メッセージの収集と検証

アクターから送信されるメッセージを収集し、後で検証する手法：

```scala
val events = ArrayBuffer.empty[TestMessage]
// ... アクター処理 ...
eventually {
  events.size shouldBe 1
  // ... 検証 ...
}
```

### 5. テスト用の型変換やキャスト

型安全性を確保しつつ、テスト用のキャストを行う手法：

```scala
val TestMessage.EventPersisted(evts) = events.head: @unchecked
```

## BankAccountサンプルのテスト

実際のアプリケーションに近いシナリオでは、BankAccountのサンプル実装を使ってテストを行っています：

```scala
class InMemoryBankAccountAggregateSpec extends BankAccountAggregateTestBase {
  // InMemoryモードを使用
  override def persistenceMode: PersistenceMode = PersistenceMode.InMemory
  
  // ... テストケース ...
}
```

このテストでは：

1. 銀行口座の作成
2. 入金処理
3. 出金処理
4. 残高照会

などの実際のユースケースをシミュレートして、整合性を検証しています。

## テスト後のクリーンアップ

テスト間の干渉を防ぐため、特にInMemoryモードではテスト後にストアをクリアしています：

```scala
override def afterAll(): Unit = {
  InMemoryEventStore.clear()
  super.afterAll()
}
```

## まとめ

eff-sm-splitterのテスト戦略は、以下の点で優れています：

1. **抽象化されたテスト基盤**: 共通のテストロジックを抽象基底クラスに集約
2. **モード横断のテスト**: InMemoryとPersistedの両方のモードでの動作検証
3. **特殊ケースのテスト**: スナップショットや保持ポリシーなどの高度な機能のテスト
4. **実際のユースケース**: 具体的なドメインモデル（BankAccount）を使った検証
5. **非同期処理の扱い**: アクターモデルの非同期性を考慮したテスト手法

これらのテスト戦略により、ライブラリの堅牢性と信頼性が確保されています。

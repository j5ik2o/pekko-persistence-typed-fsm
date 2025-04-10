# Pekko Persistence Typedの実装方法の改善について

## 目的

このドキュメントの目的は、Pekko Persistence Typedの実装方法の改善についての提案です。

## 問題

Pekko Persistence Typedの現在の実装では、次のような問題があります。

- 問題1: これまでのアクタープログラミングのスタイルとは大きく異なります。
- 問題2: 複雑な状態遷移を実装する場合は、match/caseが複雑になり保守性が低下します。
- 問題3: コマンドハンドラでは新しい状態に遷移することができないため、ドメインオブジェクトを使いにくい

この問題をわかりやすくするために、銀行口座集約を例にして解説します。

## 通常のアクタープログラミングではどうなるか

Behaviorを使って状態遷移を記述できます。

```scala
object BankAccountAggregate {
  private final case class Created(aggregateId: BankAccountAggregateId, bankAccount: BankAccount)

  def apply(aggregateId: BankAccountAggregateId): Behavior[BankAccountCommands.Command] = {
    notCreated(aggregateId)
  }

  private def notCreated(
      aggregateId: BankAccountAggregateId
  ): Behavior[BankAccountCommands.Command] =
    Behaviors.receiveMessagePartial {
      // 口座の開設
      case command: BankAccountCommands.CreateBankAccount if command.aggregateId == aggregateId =>
        command.replyTo ! BankAccountCommands.CreateBankAccountSucceeded(command.aggregateId)
        created(Created(aggregateId, bankAccount = BankAccount(command.aggregateId.toEntityId)))
    }

  private def created(state: Created): Behavior[BankAccountCommands.Command] =
    Behaviors.receiveMessagePartial {
      // 残高の取得
      case BankAccountCommands.GetBalance(aggregateId, replyTo) if aggregateId == state.aggregateId =>
        replyTo ! BankAccountCommands.GetBalanceReply(aggregateId, state.bankAccount.balance)
        Behaviors.same
      // 現金の入金
      case BankAccountCommands.DepositCash(aggregateId, amount, replyTo) if aggregateId == state.aggregateId =>
        state.bankAccount.add(amount) match {
          case Right(result) =>
            replyTo ! BankAccountCommands.DepositCashSucceeded(aggregateId)
            created(state.copy(bankAccount = result))
          case Left(error) =>
            replyTo ! BankAccountCommands.DepositCashFailed(aggregateId, error)
            Behaviors.same
        }
      // 現金の出金
      case BankAccountCommands.WithdrawCash(aggregateId, amount, replyTo) if aggregateId == state.aggregateId =>
        state.bankAccount.subtract(amount) match {
          case Right(result) =>
            replyTo ! BankAccountCommands.WithdrawCashSucceeded(aggregateId)
            created(state.copy(bankAccount = result))
          case Left(error) =>
            replyTo ! BankAccountCommands.WithdrawCashFailed(aggregateId, error)
            Behaviors.same
        }

    }
}
```


## Akka Persistence Typed での問題

- EventSourcedBehaviorに従う必要があるため、コマンドハンドラはBehaviorを返せません。漸進的な実装がしにくい。
- StateとCommandが複雑な場合はコマンドハンドラの保守性が下がります。→これについては分割して記述するなど対策はあります。
- 状態更新を扱えないとなると、ロジックをドメインオブジェクトに委譲しにくい。DDDとの相性がよくないです。

```scala
/** このスタイルの問題
  *
  *   - デメリット
  *     - Behaviorを使ったアクタープログラミングができない。状態が複雑な場合は保守性が下がる
  *     - コマンドハンドラでドメインオブジェクトが使いにくい
  *   - メリット
  *     - 記述するコード量が少ない
  */
object BankAccountAggregate {

  private[styleDefault] object States {
    sealed trait State
    final case object NotCreated extends State
    final case class Created(aggregateId: BankAccountAggregateId, bankAccount: BankAccount) extends State
  }

  def apply(aggregateId: BankAccountAggregateId): Behavior[BankAccountCommands.Command] = {
    EventSourcedBehavior.withEnforcedReplies(
      persistenceId = PersistenceId.ofUniqueId(aggregateId.asString),
      emptyState = States.NotCreated,
      commandHandler,
      eventHandler
    )
  }

  private def commandHandler
      : (States.State, BankAccountCommands.Command) => ReplyEffect[BankAccountEvents.Event, States.State] = {
    // 口座残高の取得
    case (Created(_, bankAccount), BankAccountCommands.GetBalance(aggregateId, replyTo)) =>
      Effect.reply(replyTo)(BankAccountCommands.GetBalanceReply(aggregateId, bankAccount.balance))
    // 口座開設コマンド
    case (_, BankAccountCommands.CreateBankAccount(aggregateId, replyTo)) =>
      Effect.persist(BankAccountEvents.BankAccountCreated(aggregateId, Instant.now())).thenReply(replyTo) { _ =>
        BankAccountCommands.CreateBankAccountSucceeded(aggregateId)
      }
    // 現金の入金
    case (state: Created, BankAccountCommands.DepositCash(aggregateId, amount, replyTo)) =>
      // NOTE: コマンドはドメインロジックを呼び出す
      state.bankAccount.add(amount) match {
        // NOTE: コマンドハンドラではステートを更新できないので、戻り値は捨てることになる…
        case Right(_) =>
          Effect.persist(BankAccountEvents.CashDeposited(aggregateId, amount, Instant.now())).thenReply(replyTo) { _ =>
            BankAccountCommands.DepositCashSucceeded(aggregateId)
          }
        case Left(error) =>
          Effect.reply(replyTo)(BankAccountCommands.DepositCashFailed(aggregateId, error))
      }
    // 現金の出金
    case (state: Created, BankAccountCommands.WithdrawCash(aggregateId, amount, replyTo)) =>
      state.bankAccount.subtract(amount) match {
        case Right(_) =>
          Effect.persist(BankAccountEvents.CashWithdrew(aggregateId, amount, Instant.now())).thenReply(replyTo) { _ =>
            BankAccountCommands.WithdrawCashSucceeded(aggregateId)
          }
        case Left(error) =>
          Effect.reply(replyTo)(BankAccountCommands.WithdrawCashFailed(aggregateId, error))
      }
  }

  private def eventHandler: (States.State, BankAccountEvents.Event) => States.State = {
    case (_, BankAccountEvents.BankAccountCreated(aggregateId, _)) =>
      Created(aggregateId, bankAccount = BankAccount(aggregateId.toEntityId))
    case (Created(_, bankAccount), BankAccountEvents.CashDeposited(aggregateId, amount, _)) =>
      // NOTE: イベントハンドラでも結局Eitherは使う価値がない...
      bankAccount
        .add(amount).fold(
          { error => throw new Exception(s"error = $error") },
          { result => Created(aggregateId, bankAccount = result) }
        )
    case (Created(_, bankAccount), BankAccountEvents.CashWithdrew(aggregateId, amount, _)) =>
      bankAccount
        .subtract(amount).fold(
          { error => throw new Exception(s"error = $error") },
          { result => Created(aggregateId, bankAccount = result) }
        )
  }

}
```

## 新しい書き方の提案

- この方法ではEventSourcedBehaviorを集約アクターの子アクターとするため、上記の問題を解消できます。
- 通常のアクタープログラミングの実装方法をそのまま適用可能です。
- 通常のアクタープログラミングの実装から永続化対応されることが比較的容易です。
- 実装例では完全なインメモリモードを提供しているので、初期実装を書く上ではPekko Persistence Typedさえ不要になります。

### InMemoryEffectorについて

このライブラリの特長的な機能として、`InMemoryEffector`があります。これは`PersistenceEffector`の実装の一つで、イベントとスナップショットをメモリ内に保存します。

```scala
final class InMemoryEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
) extends PersistenceEffector[S, E, M]
```

#### 主な特徴
- シングルトンの`InMemoryEventStore`を使用してイベントとスナップショットをメモリに保存します
- データベース設定なしで開発やテストを素早く行えます
- アクター初期化時に保存されたイベントから状態を自動的に復元します
- 実際のデータベース操作のような遅延なしで即時に永続化処理を行います

#### 開発ワークフローの改善

`InMemoryEffector`を使用することで、次のような開発ワークフローが可能になります：

1. 最初はインメモリモードで開発を開始し、ビジネスロジックに集中
2. ユニットテストを実装し、機能を検証
3. アプリケーションが成熟した段階で、実際の永続化を設定

このアプローチにより、開発初期段階ではデータベースの設定や永続化の詳細について考慮する必要がなく、ビジネスロジックの実装に集中できます。また、テストも高速に実行できるため、開発サイクルを短縮できます。

```scala
object BankAccountAggregate {
  def actorName(aggregateId: BankAccountId): String =
    s"${aggregateId.aggregateTypeName}-${aggregateId.asString}"

  enum State {
    def aggregateId: BankAccountId
    case NotCreated(aggregateId: BankAccountId)
    case Created(aggregateId: BankAccountId, bankAccount: BankAccount)

    def applyEvent(event: BankAccountEvent): State = (this, event) match {
      case (State.NotCreated(aggregateId), BankAccountEvent.Created(id, _)) =>
        Created(id, BankAccount(id))
      case (State.Created(id, bankAccount), BankAccountEvent.CashDeposited(_, amount, _)) =>
        bankAccount
          .add(amount)
          .fold(
            error => throw new IllegalStateException(s"Failed to apply event: $error"),
            result => State.Created(id, result.bankAccount),
          )
      case (State.Created(id, bankAccount), BankAccountEvent.CashWithdrew(_, amount, _)) =>
        bankAccount
          .subtract(amount)
          .fold(
            error => throw new IllegalStateException(s"Failed to apply event: $error"),
            result => State.Created(id, result.bankAccount),
          )
      case _ =>
        throw new IllegalStateException(
          s"Invalid state transition: $this -> $event",
        )
    }
  }

  def apply(
             aggregateId: BankAccountId,
           ): Behavior[BankAccountCommand] = {
    val config = PersistenceEffectorConfig[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
      persistenceId = actorName(aggregateId),
      initialState = State.NotCreated(aggregateId),
      applyEvent = (state, event) => state.applyEvent(event),
      messageConverter = BankAccountCommand.messageConverter,
    )
    Behaviors.setup[BankAccountCommand] { implicit ctx =>
      PersistenceEffector.create[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand](
        config,
      ) {
        case (initialState: State.NotCreated, effector) =>
          handleNotCreated(initialState, effector)
        case (initialState: State.Created, effector) =>
          handleCreated(initialState, effector)
      }
    }
  }

  private def handleNotCreated(
                                state: BankAccountAggregate.State.NotCreated,
                                effector: PersistenceEffector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
  : Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial { case cmd: BankAccountCommand.Create =>
      val Result(bankAccount, event) = BankAccount.create(cmd.aggregateId)
      effector.persistEvent(event) { _ =>
        cmd.replyTo ! CreateReply.Succeeded(cmd.aggregateId)
        handleCreated(State.Created(state.aggregateId, bankAccount), effector)
      }
    }

  private def handleCreated(
                             state: BankAccountAggregate.State.Created,
                             effector: PersistenceEffector[BankAccountAggregate.State, BankAccountEvent, BankAccountCommand])
  : Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial {
      case BankAccountCommand.Stop(aggregateId, replyTo) =>
        replyTo ! StopReply.Succeeded(aggregateId)
        Behaviors.stopped
      case BankAccountCommand.GetBalance(aggregateId, replyTo) =>
        replyTo ! GetBalanceReply.Succeeded(aggregateId, state.bankAccount.balance)
        Behaviors.same
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
      case BankAccountCommand.WithdrawCash(aggregateId, amount, replyTo) =>
        state.bankAccount
          .subtract(amount)
          .fold(
            error => {
              replyTo ! WithdrawCashReply.Failed(aggregateId, error)
              Behaviors.same
            },
            { case Result(newBankAccount, event) =>
              effector.persistEvent(event) { _ =>
                replyTo ! WithdrawCashReply.Succeeded(aggregateId, amount)
                handleCreated(state.copy(bankAccount = newBankAccount), effector)
              }
            },
          )
    }
}
```

## まとめ

以下のようなケースに当てはまる場合はこの方法を検討してください。

- 最初から永続化を考慮しないでステップバイステップで実装する場合は、新しい書き方をする
- 状態遷移が複雑な場合は、新しい書き方をする

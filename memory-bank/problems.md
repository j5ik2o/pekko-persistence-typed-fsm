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
- 実装例では完全なインメモリモードを提供しているので、初期実装を書く上ではAkka Persistence Typedさえ不要になります。

[BankAccountAggregate](https://github.com/j5ik2o/akka-at-least-once-delivery/blob/main/src/main/scala/example/persistence/styleEffector/BankAccountAggregate.scala)


## まとめ

以下のようなケースに当てはまる場合はこの方法を検討してください。

- 最初から永続化を考慮しないでステップバイステップで実装する場合は、新しい書き方をする
- 状態遷移が複雑な場合は、新しい書き方をする
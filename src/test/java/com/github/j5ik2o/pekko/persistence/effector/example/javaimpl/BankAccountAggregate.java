package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceEffector;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceMode;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.RetentionCriteria;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.SnapshotCriteria;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.io.Serializable;
import java.util.Optional;

/**
 * 銀行口座のアグリゲート
 */
public class BankAccountAggregate {
  /**
   * アクター名を取得する
   *
   * @param aggregateId アグリゲートID
   * @return アクター名
   */
  public static String actorName(BankAccountId aggregateId) {
    return aggregateId.getAggregateTypeName() + "-" + aggregateId.asString();
  }

  /**
   * 銀行口座の状態を表すシールドインターフェース
   */
  public sealed interface State {
    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    BankAccountId getAggregateId();

    /**
     * イベントを適用する
     *
     * @param event イベント
     * @return 新しい状態
     */
    State applyEvent(BankAccountEvent event);

    /**
     * 未作成状態
     *
     * @param aggregateId アグリゲートID
     */
    record NotCreated(BankAccountId aggregateId) implements State, Serializable {
      @Override
      public BankAccountId getAggregateId() {
        return aggregateId;
      }

      @Override
      public State applyEvent(BankAccountEvent event) {
        if (event instanceof BankAccountEvent.Created created) {
          return new Created(created.getAggregateId(), BankAccount.apply(created.getAggregateId()));
        } else {
          throw new IllegalStateException("Invalid state transition: " + this + " -> " + event);
        }
      }
    }

    /**
     * 作成済み状態
     *
     * @param aggregateId アグリゲートID
     * @param bankAccount 銀行口座
     */
    record Created(BankAccountId aggregateId, BankAccount bankAccount) implements State, Serializable {
      @Override
      public BankAccountId getAggregateId() {
        return aggregateId;
      }

      @Override
      public State applyEvent(BankAccountEvent event) {
        if (event instanceof BankAccountEvent.CashDeposited deposited) {
          var result = bankAccount.add(deposited.amount());
          if (result.isLeft()) {
            throw new IllegalStateException("Failed to apply event: " + result.getLeft());
          } else {
            return new Created(aggregateId, result.getRight().getState());
          }
        } else if (event instanceof BankAccountEvent.CashWithdrew withdrew) {
          var result = bankAccount.subtract(withdrew.amount());
          if (result.isLeft()) {
            throw new IllegalStateException("Failed to apply event: " + result.getLeft());
          } else {
            return new Created(aggregateId, result.getRight().getState());
          }
        } else {
          throw new IllegalStateException("Invalid state transition: " + this + " -> " + event);
        }
      }
    }
  }

  /**
   * 銀行口座のアクターを作成する
   *
   * @param aggregateId     アグリゲートID
   * @param persistenceMode 永続化モード
   * @return アクターの振る舞い
   */
  public static Behavior<BankAccountCommand> create(
    BankAccountId aggregateId,
    PersistenceMode persistenceMode
  ) {
    return Behaviors.setup(ctx -> {
      ctx.getLog().debug("Creating BankAccount actor: {}", actorName(aggregateId));

      // メッセージコンバーターを作成
      var messageConverter = new BankAccountCommand.Protocol().getJavaMessageConverter();

      // 永続化モードに応じたPersistenceEffectorを作成
      if (persistenceMode == PersistenceMode.IN_MEMORY) {
        // インメモリモードの場合
        ctx.getLog().debug("Using IN_MEMORY mode for {}", aggregateId);
        return PersistenceEffector.createInMemory(
          actorName(aggregateId),
          new State.NotCreated(aggregateId),
          State::applyEvent,
          messageConverter,
          32,
          Optional.of(SnapshotCriteria.every(2)),
          Optional.of(RetentionCriteria.ofSnapshotEvery(2)),
          Optional.empty(),
          (state, effector) -> {
            ctx.getLog().debug("Handling state: {}", state);

            // 状態に応じたハンドラーを呼び出す
            if (state instanceof State.NotCreated) {
              return handleNotCreated((State.NotCreated) state, effector, ctx);
            } else if (state instanceof State.Created) {
              return handleCreated((State.Created) state, effector, ctx);
            } else {
              ctx.getLog().error("Unknown state: {}", state);
              throw new IllegalStateException("Unknown state: " + state);
            }
          }
        );
      } else {
        // 通常の永続化モードの場合
        ctx.getLog().debug("Using PERSISTENCE mode for {}", aggregateId);
        return PersistenceEffector.create(
          actorName(aggregateId),
          new State.NotCreated(aggregateId),
          State::applyEvent,
          messageConverter,
          32,
          Optional.of(SnapshotCriteria.every(2)),
          Optional.of(RetentionCriteria.ofSnapshotEvery(2)),
          Optional.empty(),
          (state, effector) -> {
            ctx.getLog().debug("Handling state: {}", state);

            // 状態に応じたハンドラーを呼び出す
            if (state instanceof State.NotCreated) {
              return handleNotCreated((State.NotCreated) state, effector, ctx);
            } else if (state instanceof State.Created) {
              return handleCreated((State.Created) state, effector, ctx);
            } else {
              ctx.getLog().error("Unknown state: {}", state);
              throw new IllegalStateException("Unknown state: " + state);
            }
          }
        );
      }
    });
  }

  /**
   * 未作成状態を処理する
   *
   * @param state    状態
   * @param effector エフェクター
   * @param ctx      アクターコンテキスト
   * @return アクターの振る舞い
   */
  private static Behavior<BankAccountCommand> handleNotCreated(
    State.NotCreated state,
    PersistenceEffector<State, BankAccountEvent, BankAccountCommand> effector,
    ActorContext<BankAccountCommand> ctx) {
    ctx.getLog().debug("Handling NotCreated state for aggregate: {}", state.getAggregateId());

    // 基本的なビヘイビアを定義
    return Behaviors.receive(BankAccountCommand.class)
      .onMessage(BankAccountCommand.Create.class, cmd -> {
        ctx.getLog().debug("Received Create command: {}", cmd);
        var result = BankAccount.create(cmd.aggregateId());
        ctx.getLog().debug("Created BankAccount persisting event: {}", result.getEvent());

        // イベントを永続化する前に応答を送信
        cmd.replyTo().tell(CreateReply.succeeded(cmd.aggregateId()));

        // イベントを永続化
        return effector.persistEvent(result.getEvent(), event -> {
          ctx.getLog().debug("Event persisted callback");
          var created = new State.Created(state.aggregateId(), result.getState());
          return handleCreated(created, effector, ctx);
        });
      })
      .onAnyMessage(msg -> {
        ctx.getLog().debug("Received message during recovery: {}", msg);
        return Behaviors.same();
      })
      .build();
  }

  /**
   * 作成済み状態を処理する
   *
   * @param state    状態
   * @param effector エフェクター
   * @param ctx      アクターコンテキスト
   * @return アクターの振る舞い
   */
  private static Behavior<BankAccountCommand> handleCreated(
    State.Created state,
    PersistenceEffector<State, BankAccountEvent, BankAccountCommand> effector,
    ActorContext<BankAccountCommand> ctx) {
    ctx.getLog().debug("Handling Created state for aggregate: {}, balance: {}",
      state.getAggregateId(), state.bankAccount().getBalance());

    // 基本的なビヘイビアを先に定義
    var behavior = Behaviors.receive(BankAccountCommand.class)
      .onMessage(BankAccountCommand.Stop.class, cmd -> {
        ctx.getLog().debug("Received Stop command: {}", cmd);
        cmd.replyTo().tell(StopReply.succeeded(cmd.aggregateId()));
        ctx.getLog().debug("Stopping actor for aggregate: {}", cmd.aggregateId());
        return Behaviors.stopped();
      })
      .onMessage(BankAccountCommand.GetBalance.class, cmd -> {
        ctx.getLog().debug("Received GetBalance command: {}", cmd);
        var balance = state.bankAccount().getBalance();
        ctx.getLog().debug("Current balance for {}: {}", cmd.aggregateId(), balance);
        cmd.replyTo().tell(GetBalanceReply.succeeded(cmd.aggregateId(), balance));
        return Behaviors.same();
      })
      .onMessage(BankAccountCommand.DepositCash.class, cmd -> {
        ctx.getLog().debug("Received DepositCash command: {}, amount: {}", cmd, cmd.amount());
        var result = state.bankAccount().add(cmd.amount());
        if (result.isLeft()) {
          var error = result.getLeft();
          ctx.getLog().error("Failed to deposit cash: {}", error);
          cmd.replyTo().tell(DepositCashReply.failed(cmd.aggregateId(), error));
          return Behaviors.same();
        } else {
          var r = result.getRight();
          ctx.getLog().debug("Cash deposited, persisting event: {}", r.getEvent());

          // 応答を先に送信
          cmd.replyTo().tell(DepositCashReply.succeeded(cmd.aggregateId(), cmd.amount()));

          return effector.persistEvent(r.getEvent(), event -> {
            ctx.getLog().debug("Event persisted callback, new balance: {}", r.getState().getBalance());
            return handleCreated(new State.Created(state.aggregateId(), r.getState()), effector, ctx);
          });
        }
      })
      .onMessage(BankAccountCommand.WithdrawCash.class, cmd -> {
        ctx.getLog().debug("Received WithdrawCash command: {}, amount: {}", cmd, cmd.amount());
        var result = state.bankAccount().subtract(cmd.amount());
        if (result.isLeft()) {
          var error = result.getLeft();
          ctx.getLog().error("Failed to withdraw cash: {}", error);
          cmd.replyTo().tell(WithdrawCashReply.failed(cmd.aggregateId(), error));
          return Behaviors.same();
        } else {
          var r = result.getRight();
          ctx.getLog().debug("Cash withdrawn, persisting event: {}", r.getEvent());

          // 応答を先に送信
          cmd.replyTo().tell(WithdrawCashReply.succeeded(cmd.aggregateId(), cmd.amount()));

          return effector.persistEvent(r.getEvent(), event -> {
            ctx.getLog().debug("Event persisted callback, new balance: {}", r.getState().getBalance());
            return handleCreated(new State.Created(state.aggregateId(), r.getState()), effector, ctx);
          });
        }
      })
      .onMessage(BankAccountCommand.StatePersisted.class, msg -> {
        ctx.getLog().debug("Received StatePersisted message: {}", msg);
        return Behaviors.same();
      })
      .onMessage(BankAccountCommand.EventPersisted.class, msg -> {
        ctx.getLog().debug("Received EventPersisted message: {}", msg);
        if (!msg.events().isEmpty()) {
          BankAccountEvent event = msg.events().get(0);
          if (event instanceof BankAccountEvent.CashDeposited deposited) {
            ctx.getLog().debug("Processing persisted CashDeposited event");
            var result = state.bankAccount().add(deposited.amount());
            if (result.isRight()) {
              return handleCreated(new State.Created(state.aggregateId(), result.getRight().getState()), effector, ctx);
            }
          } else if (event instanceof BankAccountEvent.CashWithdrew withdrew) {
            ctx.getLog().debug("Processing persisted CashWithdrew event");
            var result = state.bankAccount().subtract(withdrew.amount());
            if (result.isRight()) {
              return handleCreated(new State.Created(state.aggregateId(), result.getRight().getState()), effector, ctx);
            }
          }
        }
        return Behaviors.same();
      })
      .build();

    // スナップショットを永続化（ビヘイビアを先に定義した後）
    return effector.persistSnapshot(state, snapshot -> {
      ctx.getLog().debug("Snapshot persisted for state: {}", snapshot);
      return behavior;
    });
  }
}

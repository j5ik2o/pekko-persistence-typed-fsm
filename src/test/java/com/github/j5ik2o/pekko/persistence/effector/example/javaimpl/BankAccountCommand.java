package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import com.github.j5ik2o.pekko.persistence.effector.javadsl.DeletedSnapshots;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.MessageConverter;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.MessageProtocol;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistedEvent;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistedState;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.RecoveredState;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.List;

/**
 * 銀行口座のコマンドを表すシールドインターフェース
 */
public sealed interface BankAccountCommand {
    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    BankAccountId getAggregateId();

    /**
     * 残高取得コマンド
     *
     * @param aggregateId アグリゲートID
     * @param replyTo 返信先
     */
    record GetBalance(BankAccountId aggregateId, ActorRef<GetBalanceReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * 停止コマンド
     *
     * @param aggregateId アグリゲートID
     * @param replyTo 返信先
     */
    record Stop(BankAccountId aggregateId, ActorRef<StopReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * 作成コマンド
     *
     * @param aggregateId アグリゲートID
     * @param replyTo 返信先
     */
    record Create(BankAccountId aggregateId, ActorRef<CreateReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * 入金コマンド
     *
     * @param aggregateId アグリゲートID
     * @param amount 金額
     * @param replyTo 返信先
     */
    record DepositCash(BankAccountId aggregateId, Money amount, ActorRef<DepositCashReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * 出金コマンド
     *
     * @param aggregateId アグリゲートID
     * @param amount 金額
     * @param replyTo 返信先
     */
    record WithdrawCash(BankAccountId aggregateId, Money amount, ActorRef<WithdrawCashReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * 状態復元コマンド
     *
     * @param state 状態
     */
    record StateRecovered(BankAccountAggregate.State state) implements BankAccountCommand, RecoveredState<BankAccountAggregate.State, BankAccountCommand> {
        @Override
        public BankAccountId getAggregateId() {
            return state.getAggregateId();
        }

        @Override
        public BankAccountAggregate.State state() {
            return state;
        }
    }

    /**
     * イベント永続化コマンド
     *
     * @param events イベント
     */
    record EventPersisted(List<BankAccountEvent> events) implements BankAccountCommand, PersistedEvent<BankAccountEvent, BankAccountCommand> {
        @Override
        public BankAccountId getAggregateId() {
            throw new UnsupportedOperationException("EventPersisted does not have aggregateId");
        }

        @Override
        public List<BankAccountEvent> events() {
            return events;
        }
    }

    /**
     * 状態永続化コマンド
     *
     * @param state 状態
     */
    record StatePersisted(BankAccountAggregate.State state) implements BankAccountCommand, PersistedState<BankAccountAggregate.State, BankAccountCommand> {
        @Override
        public BankAccountId getAggregateId() {
            throw new UnsupportedOperationException("StatePersisted does not have aggregateId");
        }

        @Override
        public BankAccountAggregate.State state() {
            return state;
        }
    }

    /**
     * スナップショット削除コマンド
     *
     * @param maxSequenceNumber 最大シーケンス番号
     */
    record SnapshotShotsDeleted(long maxSequenceNumber) implements BankAccountCommand, DeletedSnapshots<BankAccountCommand> {
        @Override
        public BankAccountId getAggregateId() {
            throw new UnsupportedOperationException("SnapshotShotsDeleted does not have aggregateId");
        }

        @Override
        public long maxSequenceNumber() {
            return maxSequenceNumber;
        }
    }

    /**
     * メッセージプロトコル
     */
    class Protocol implements MessageProtocol<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand> {
        private final com.github.j5ik2o.pekko.persistence.effector.javadsl.MessageConverter<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand> messageConverter;

        /**
         * コンストラクタ
         */
        public Protocol() {
            this.messageConverter = com.github.j5ik2o.pekko.persistence.effector.javadsl.MessageConverter.create(
                    EventPersisted::new,
                    StatePersisted::new,
                    StateRecovered::new,
                    SnapshotShotsDeleted::new
            );
        }

        @Override
        public com.github.j5ik2o.pekko.persistence.effector.scaladsl.MessageConverter<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand> messageConverter() {
            // JavaDSL版のMessageConverterからScalaDSL版のMessageConverterを作成
            return messageConverter.toScala();
        }

        // JavaDSL版のMessageConverterを取得するメソッドを追加
        public com.github.j5ik2o.pekko.persistence.effector.javadsl.MessageConverter<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand> getJavaMessageConverter() {
            return messageConverter;
        }
    }
}

/**
 * 停止応答
 */
enum StopReply {
    /**
     * 成功
     */
    SUCCEEDED;

    private BankAccountId aggregateId;

    /**
     * 成功応答を作成する
     *
     * @param aggregateId アグリゲートID
     * @return 成功応答
     */
    public static StopReply succeeded(BankAccountId aggregateId) {
        StopReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        return reply;
    }

    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }
}

/**
 * 残高取得応答
 */
enum GetBalanceReply {
    /**
     * 成功
     */
    SUCCEEDED;

    private BankAccountId aggregateId;
    private Money balance;

    /**
     * 成功応答を作成する
     *
     * @param aggregateId アグリゲートID
     * @param balance 残高
     * @return 成功応答
     */
    public static GetBalanceReply succeeded(BankAccountId aggregateId, Money balance) {
        GetBalanceReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        reply.balance = balance;
        return reply;
    }

    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }

    /**
     * 残高を取得する
     *
     * @return 残高
     */
    public Money getBalance() {
        return balance;
    }
}

/**
 * 作成応答
 */
enum CreateReply {
    /**
     * 成功
     */
    SUCCEEDED;

    private BankAccountId aggregateId;

    /**
     * 成功応答を作成する
     *
     * @param aggregateId アグリゲートID
     * @return 成功応答
     */
    public static CreateReply succeeded(BankAccountId aggregateId) {
        CreateReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        return reply;
    }

    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }
}

/**
 * 入金応答
 */
enum DepositCashReply {
    /**
     * 成功
     */
    SUCCEEDED,
    /**
     * 失敗
     */
    FAILED;

    private BankAccountId aggregateId;
    private Money amount;
    private BankAccountError error;

    /**
     * 成功応答を作成する
     *
     * @param aggregateId アグリゲートID
     * @param amount 金額
     * @return 成功応答
     */
    public static DepositCashReply succeeded(BankAccountId aggregateId, Money amount) {
        DepositCashReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        reply.amount = amount;
        return reply;
    }

    /**
     * 失敗応答を作成する
     *
     * @param aggregateId アグリゲートID
     * @param error エラー
     * @return 失敗応答
     */
    public static DepositCashReply failed(BankAccountId aggregateId, BankAccountError error) {
        DepositCashReply reply = FAILED;
        reply.aggregateId = aggregateId;
        reply.error = error;
        return reply;
    }

    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }

    /**
     * 金額を取得する
     *
     * @return 金額
     */
    public Money getAmount() {
        return amount;
    }

    /**
     * エラーを取得する
     *
     * @return エラー
     */
    public BankAccountError getError() {
        return error;
    }
}

/**
 * 出金応答
 */
enum WithdrawCashReply {
    /**
     * 成功
     */
    SUCCEEDED,
    /**
     * 失敗
     */
    FAILED;

    private BankAccountId aggregateId;
    private Money amount;
    private BankAccountError error;

    /**
     * 成功応答を作成する
     *
     * @param aggregateId アグリゲートID
     * @param amount 金額
     * @return 成功応答
     */
    public static WithdrawCashReply succeeded(BankAccountId aggregateId, Money amount) {
        WithdrawCashReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        reply.amount = amount;
        return reply;
    }

    /**
     * 失敗応答を作成する
     *
     * @param aggregateId アグリゲートID
     * @param error エラー
     * @return 失敗応答
     */
    public static WithdrawCashReply failed(BankAccountId aggregateId, BankAccountError error) {
        WithdrawCashReply reply = FAILED;
        reply.aggregateId = aggregateId;
        reply.error = error;
        return reply;
    }

    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }

    /**
     * 金額を取得する
     *
     * @return 金額
     */
    public Money getAmount() {
        return amount;
    }

    /**
     * エラーを取得する
     *
     * @return エラー
     */
    public BankAccountError getError() {
        return error;
    }
}

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
 * Sealed interface representing bank account commands
 */
public sealed interface BankAccountCommand {
    /**
     * Get the aggregate ID
     *
     * @return Aggregate ID
     */
    BankAccountId getAggregateId();

    /**
     * Get balance command
     *
     * @param aggregateId Aggregate ID
     * @param replyTo Reply destination
     */
    record GetBalance(BankAccountId aggregateId, ActorRef<GetBalanceReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * Stop command
     *
     * @param aggregateId Aggregate ID
     * @param replyTo Reply destination
     */
    record Stop(BankAccountId aggregateId, ActorRef<StopReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * Create command
     *
     * @param aggregateId Aggregate ID
     * @param replyTo Reply destination
     */
    record Create(BankAccountId aggregateId, ActorRef<CreateReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * Deposit cash command
     *
     * @param aggregateId Aggregate ID
     * @param amount Amount
     * @param replyTo Reply destination
     */
    record DepositCash(BankAccountId aggregateId, Money amount, ActorRef<DepositCashReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * Withdraw cash command
     *
     * @param aggregateId Aggregate ID
     * @param amount Amount
     * @param replyTo Reply destination
     */
    record WithdrawCash(BankAccountId aggregateId, Money amount, ActorRef<WithdrawCashReply> replyTo) implements BankAccountCommand {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }
    }

    /**
     * State recovered command
     *
     * @param state State
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
     * Event persisted command
     *
     * @param events Events
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
     * State persisted command
     *
     * @param state State
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
     * Snapshot shots deleted command
     *
     * @param maxSequenceNumber Maximum sequence number
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
     * Message protocol
     */
    class Protocol implements MessageProtocol<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand> {
        private final com.github.j5ik2o.pekko.persistence.effector.javadsl.MessageConverter<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand> messageConverter;

        /**
         * Constructor
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
            // Create ScalaDSL MessageConverter from JavaDSL MessageConverter
            return messageConverter.toScala();
        }

        // Add method to get JavaDSL MessageConverter
        public com.github.j5ik2o.pekko.persistence.effector.javadsl.MessageConverter<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand> getJavaMessageConverter() {
            return messageConverter;
        }
    }
}

/**
 * Stop reply
 */
enum StopReply {
    /**
     * Succeeded
     */
    SUCCEEDED;

    private BankAccountId aggregateId;

    /**
     * Create a success reply
     *
     * @param aggregateId Aggregate ID
     * @return Success reply
     */
    public static StopReply succeeded(BankAccountId aggregateId) {
        StopReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        return reply;
    }

    /**
     * Get the aggregate ID
     *
     * @return Aggregate ID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }
}

/**
 * Get balance reply
 */
enum GetBalanceReply {
    /**
     * Succeeded
     */
    SUCCEEDED;

    private BankAccountId aggregateId;
    private Money balance;

    /**
     * Create a success reply
     *
     * @param aggregateId Aggregate ID
     * @param balance Balance
     * @return Success reply
     */
    public static GetBalanceReply succeeded(BankAccountId aggregateId, Money balance) {
        GetBalanceReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        reply.balance = balance;
        return reply;
    }

    /**
     * Get the aggregate ID
     *
     * @return Aggregate ID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }

    /**
     * Get the balance
     *
     * @return Balance
     */
    public Money getBalance() {
        return balance;
    }
}

/**
 * Create reply
 */
enum CreateReply {
    /**
     * Succeeded
     */
    SUCCEEDED;

    private BankAccountId aggregateId;

    /**
     * Create a success reply
     *
     * @param aggregateId Aggregate ID
     * @return Success reply
     */
    public static CreateReply succeeded(BankAccountId aggregateId) {
        CreateReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        return reply;
    }

    /**
     * Get the aggregate ID
     *
     * @return Aggregate ID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }
}

/**
 * Deposit cash reply
 */
enum DepositCashReply {
    /**
     * Succeeded
     */
    SUCCEEDED,
    /**
     * Failed
     */
    FAILED;

    private BankAccountId aggregateId;
    private Money amount;
    private BankAccountError error;

    /**
     * Create a success reply
     *
     * @param aggregateId Aggregate ID
     * @param amount Amount
     * @return Success reply
     */
    public static DepositCashReply succeeded(BankAccountId aggregateId, Money amount) {
        DepositCashReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        reply.amount = amount;
        return reply;
    }

    /**
     * Create a failure reply
     *
     * @param aggregateId Aggregate ID
     * @param error Error
     * @return Failure reply
     */
    public static DepositCashReply failed(BankAccountId aggregateId, BankAccountError error) {
        DepositCashReply reply = FAILED;
        reply.aggregateId = aggregateId;
        reply.error = error;
        return reply;
    }

    /**
     * Get the aggregate ID
     *
     * @return Aggregate ID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }

    /**
     * Get the amount
     *
     * @return Amount
     */
    public Money getAmount() {
        return amount;
    }

    /**
     * Get the error
     *
     * @return Error
     */
    public BankAccountError getError() {
        return error;
    }
}

/**
 * Withdraw cash reply
 */
enum WithdrawCashReply {
    /**
     * Succeeded
     */
    SUCCEEDED,
    /**
     * Failed
     */
    FAILED;

    private BankAccountId aggregateId;
    private Money amount;
    private BankAccountError error;

    /**
     * Create a success reply
     *
     * @param aggregateId Aggregate ID
     * @param amount Amount
     * @return Success reply
     */
    public static WithdrawCashReply succeeded(BankAccountId aggregateId, Money amount) {
        WithdrawCashReply reply = SUCCEEDED;
        reply.aggregateId = aggregateId;
        reply.amount = amount;
        return reply;
    }

    /**
     * Create a failure reply
     *
     * @param aggregateId Aggregate ID
     * @param error Error
     * @return Failure reply
     */
    public static WithdrawCashReply failed(BankAccountId aggregateId, BankAccountError error) {
        WithdrawCashReply reply = FAILED;
        reply.aggregateId = aggregateId;
        reply.error = error;
        return reply;
    }

    /**
     * Get the aggregate ID
     *
     * @return Aggregate ID
     */
    public BankAccountId getAggregateId() {
        return aggregateId;
    }

    /**
     * Get the amount
     *
     * @return Amount
     */
    public Money getAmount() {
        return amount;
    }

    /**
     * Get the error
     *
     * @return Error
     */
    public BankAccountError getError() {
        return error;
    }
}

package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import java.time.Instant;

/**
 * Sealed interface representing bank account events
 */
public sealed interface BankAccountEvent extends Serializable {
    /**
     * Get the aggregate ID
     *
     * @return Aggregate ID
     */
    BankAccountId getAggregateId();

    /**
     * Get the occurrence time
     *
     * @return Occurrence time
     */
    Instant getOccurredAt();

    /**
     * Created event
     *
     * @param aggregateId Aggregate ID
     * @param occurredAt Occurrence time
     */
    record Created(BankAccountId aggregateId, Instant occurredAt) implements BankAccountEvent {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    /**
     * Cash deposited event
     *
     * @param aggregateId Aggregate ID
     * @param amount Amount
     * @param occurredAt Occurrence time
     */
    record CashDeposited(BankAccountId aggregateId, Money amount, Instant occurredAt) implements BankAccountEvent {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    /**
     * Cash withdrew event
     *
     * @param aggregateId Aggregate ID
     * @param amount Amount
     * @param occurredAt Occurrence time
     */
    record CashWithdrew(BankAccountId aggregateId, Money amount, Instant occurredAt) implements BankAccountEvent {
        @Override
        public BankAccountId getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getOccurredAt() {
            return occurredAt;
        }
    }
}

package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import java.time.Instant;

/**
 * 銀行口座のイベントを表すシールドインターフェース
 */
public sealed interface BankAccountEvent extends Serializable {
    /**
     * アグリゲートIDを取得する
     *
     * @return アグリゲートID
     */
    BankAccountId getAggregateId();

    /**
     * 発生日時を取得する
     *
     * @return 発生日時
     */
    Instant getOccurredAt();

    /**
     * 作成イベント
     *
     * @param aggregateId アグリゲートID
     * @param occurredAt 発生日時
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
     * 入金イベント
     *
     * @param aggregateId アグリゲートID
     * @param amount 金額
     * @param occurredAt 発生日時
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
     * 出金イベント
     *
     * @param aggregateId アグリゲートID
     * @param amount 金額
     * @param occurredAt 発生日時
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

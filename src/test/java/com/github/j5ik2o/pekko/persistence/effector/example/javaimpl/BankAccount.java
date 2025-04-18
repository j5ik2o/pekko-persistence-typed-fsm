package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

/**
 * 銀行口座を表すクラス
 */
public class BankAccount implements Serializable {
    private final BankAccountId bankAccountId;
    private final Money limit;
    private final Money balance;

    /**
     * コンストラクタ
     *
     * @param bankAccountId 銀行口座ID
     * @param limit 限度額
     * @param balance 残高
     */
    public BankAccount(BankAccountId bankAccountId, Money limit, Money balance) {
        this.bankAccountId = bankAccountId;
        this.limit = limit;
        this.balance = balance;
    }

    /**
     * 銀行口座IDを指定して銀行口座を作成する
     *
     * @param bankAccountId 銀行口座ID
     * @return 銀行口座
     */
    public static BankAccount apply(BankAccountId bankAccountId) {
        return new BankAccount(bankAccountId, Money.yens(100000), Money.zero(Money.JPY));
    }

    /**
     * 銀行口座を作成する
     *
     * @param bankAccountId 銀行口座ID
     * @param limit 限度額
     * @param balance 残高
     * @return 結果
     */
    public static Result<BankAccount, BankAccountEvent> create(
            BankAccountId bankAccountId,
            Money limit,
            Money balance) {
        return new Result<>(
                new BankAccount(bankAccountId, limit, balance),
                new BankAccountEvent.Created(bankAccountId, Instant.now())
        );
    }

    /**
     * 銀行口座IDを指定して銀行口座を作成する
     *
     * @param bankAccountId 銀行口座ID
     * @return 結果
     */
    public static Result<BankAccount, BankAccountEvent> create(BankAccountId bankAccountId) {
        return create(bankAccountId, Money.yens(100000), Money.zero(Money.JPY));
    }

    /**
     * 銀行口座IDを取得する
     *
     * @return 銀行口座ID
     */
    public BankAccountId getBankAccountId() {
        return bankAccountId;
    }

    /**
     * 限度額を取得する
     *
     * @return 限度額
     */
    public Money getLimit() {
        return limit;
    }

    /**
     * 残高を取得する
     *
     * @return 残高
     */
    public Money getBalance() {
        return balance;
    }

    /**
     * 入金する
     *
     * @param amount 金額
     * @return 結果
     */
    public Either<BankAccountError, Result<BankAccount, BankAccountEvent>> add(Money amount) {
        if (limit.isLessThan(balance.plus(amount))) {
            return Either.left(BankAccountError.LIMIT_OVER_ERROR);
        } else {
            return Either.right(
                    new Result<>(
                            new BankAccount(bankAccountId, limit, balance.plus(amount)),
                            new BankAccountEvent.CashDeposited(bankAccountId, amount, Instant.now())
                    )
            );
        }
    }

    /**
     * 出金する
     *
     * @param amount 金額
     * @return 結果
     */
    public Either<BankAccountError, Result<BankAccount, BankAccountEvent>> subtract(Money amount) {
        if (Money.zero(Money.JPY).isGreaterThan(balance.minus(amount))) {
            return Either.left(BankAccountError.INSUFFICIENT_FUNDS_ERROR);
        } else {
            return Either.right(
                    new Result<>(
                            new BankAccount(bankAccountId, limit, balance.minus(amount)),
                            new BankAccountEvent.CashWithdrew(bankAccountId, amount, Instant.now())
                    )
            );
        }
    }

    /**
     * Either型
     *
     * @param <L> 左辺の型
     * @param <R> 右辺の型
     */
    public static class Either<L, R> {
        private final Optional<L> left;
        private final Optional<R> right;

        private Either(Optional<L> left, Optional<R> right) {
            this.left = left;
            this.right = right;
        }

        public static <L, R> Either<L, R> left(L value) {
            return new Either<>(Optional.of(value), Optional.empty());
        }

        public static <L, R> Either<L, R> right(R value) {
            return new Either<>(Optional.empty(), Optional.of(value));
        }

        public boolean isLeft() {
            return left.isPresent();
        }

        public boolean isRight() {
            return right.isPresent();
        }

        public L getLeft() {
            return left.orElseThrow(() -> new IllegalStateException("Either.left is empty"));
        }

        public R getRight() {
            return right.orElseThrow(() -> new IllegalStateException("Either.right is empty"));
        }

        public <T> T fold(java.util.function.Function<L, T> leftMapper, java.util.function.Function<R, T> rightMapper) {
            if (isLeft()) {
                return leftMapper.apply(getLeft());
            } else {
                return rightMapper.apply(getRight());
            }
        }
    }
}

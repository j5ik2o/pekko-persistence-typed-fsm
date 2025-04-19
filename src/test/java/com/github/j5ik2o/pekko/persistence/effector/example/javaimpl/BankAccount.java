package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

/**
 * Class representing a bank account
 */
public class BankAccount implements Serializable {
    private final BankAccountId bankAccountId;
    private final Money limit;
    private final Money balance;

    /**
     * Constructor
     *
     * @param bankAccountId Bank account ID
     * @param limit Limit amount
     * @param balance Balance
     */
    public BankAccount(BankAccountId bankAccountId, Money limit, Money balance) {
        this.bankAccountId = bankAccountId;
        this.limit = limit;
        this.balance = balance;
    }

    /**
     * Create a bank account with the specified bank account ID
     *
     * @param bankAccountId Bank account ID
     * @return Bank account
     */
    public static BankAccount apply(BankAccountId bankAccountId) {
        return new BankAccount(bankAccountId, Money.yens(100000), Money.zero(Money.JPY));
    }

    /**
     * Create a bank account
     *
     * @param bankAccountId Bank account ID
     * @param limit Limit amount
     * @param balance Balance
     * @return Result
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
     * Create a bank account with the specified bank account ID
     *
     * @param bankAccountId Bank account ID
     * @return Result
     */
    public static Result<BankAccount, BankAccountEvent> create(BankAccountId bankAccountId) {
        return create(bankAccountId, Money.yens(100000), Money.zero(Money.JPY));
    }

    /**
     * Get the bank account ID
     *
     * @return Bank account ID
     */
    public BankAccountId getBankAccountId() {
        return bankAccountId;
    }

    /**
     * Get the limit amount
     *
     * @return Limit amount
     */
    public Money getLimit() {
        return limit;
    }

    /**
     * Get the balance
     *
     * @return Balance
     */
    public Money getBalance() {
        return balance;
    }

    /**
     * Deposit cash
     *
     * @param amount Amount
     * @return Result
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
     * Withdraw cash
     *
     * @param amount Amount
     * @return Result
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
     * Either type
     *
     * @param <L> Left type
     * @param <R> Right type
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

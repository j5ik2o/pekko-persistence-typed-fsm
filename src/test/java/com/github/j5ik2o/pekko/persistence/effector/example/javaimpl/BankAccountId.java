package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;

/**
 * 銀行口座IDを表すクラス
 */
public class BankAccountId implements Serializable {
    private final UUID value;

    /**
     * コンストラクタ
     *
     * @param value UUID
     */
    public BankAccountId(UUID value) {
        this.value = value;
    }

    /**
     * UUIDを取得する
     *
     * @return UUID
     */
    public UUID getValue() {
        return value;
    }

    /**
     * アグリゲート型名を取得する
     *
     * @return アグリゲート型名
     */
    public String getAggregateTypeName() {
        return "BankAccount";
    }

    /**
     * 文字列表現を取得する
     *
     * @return 文字列表現
     */
    public String asString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankAccountId that = (BankAccountId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "BankAccountId{" +
                "value=" + value +
                '}';
    }
}

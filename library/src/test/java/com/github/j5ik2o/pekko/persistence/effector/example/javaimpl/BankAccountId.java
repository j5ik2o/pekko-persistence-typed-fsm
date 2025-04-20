package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;

/**
 * Class representing a bank account ID
 */
public class BankAccountId implements Serializable {
    private final UUID value;

    /**
     * Constructor
     *
     * @param value UUID
     */
    public BankAccountId(UUID value) {
        this.value = value;
    }

    /**
     * Get the UUID
     *
     * @return UUID
     */
    public UUID getValue() {
        return value;
    }

    /**
     * Get the aggregate type name
     *
     * @return Aggregate type name
     */
    public String getAggregateTypeName() {
        return "BankAccount";
    }

    /**
     * Get string representation
     *
     * @return String representation
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

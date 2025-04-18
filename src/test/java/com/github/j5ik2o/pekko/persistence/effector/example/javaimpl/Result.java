package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.util.Objects;

/**
 * 結果を表すクラス
 *
 * @param <S> 状態の型
 * @param <E> イベントの型
 */
public class Result<S, E> {
    private final S state;
    private final E event;

    public Result(S state, E event) {
        this.state = state;
        this.event = event;
    }

    public S getState() {
        return state;
    }

    public E getEvent() {
        return event;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result<?, ?> result = (Result<?, ?>) o;
        return Objects.equals(state, result.state) && Objects.equals(event, result.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, event);
    }

    @Override
    public String toString() {
        return "Result{" +
                "state=" + state +
                ", event=" + event +
                '}';
    }
}

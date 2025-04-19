package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import com.github.j5ik2o.pekko.persistence.effector.javadsl.*;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.io.Serializable;

/**
 * Bank Account Aggregate
 */
public class BankAccountAggregate {
    /**
     * Get actor name
     *
     * @param aggregateId Aggregate ID
     * @return Actor name
     */
    public static String actorName(BankAccountId aggregateId) {
        return aggregateId.getAggregateTypeName() + "-" + aggregateId.asString();
    }

    /**
     * Sealed interface representing the state of a bank account
     */
    public sealed interface State {
        /**
         * Get the aggregate ID
         *
         * @return Aggregate ID
         */
        BankAccountId getAggregateId();

        /**
         * Apply an event
         *
         * @param event Event
         * @return New state
         */
        State applyEvent(BankAccountEvent event);

        /**
         * Not created state
         *
         * @param aggregateId Aggregate ID
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
         * Created state
         *
         * @param aggregateId Aggregate ID
         * @param bankAccount Bank account
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
     * Create a bank account actor
     *
     * @param aggregateId     Aggregate ID
     * @param persistenceMode Persistence mode
     * @return Actor behavior
     */
    public static Behavior<BankAccountCommand> create(
            BankAccountId aggregateId,
            PersistenceMode persistenceMode
    ) {
        return Behaviors.setup(ctx -> {
            ctx.getLog().debug("Creating BankAccount actor: {}", actorName(aggregateId));

            // Create message converter
            var messageConverter = new BankAccountCommand.Protocol().getJavaMessageConverter();

            // Create PersistenceEffectorConfig
            var config = PersistenceEffectorConfig.<BankAccountAggregate.State, BankAccountEvent, BankAccountCommand>create(
                            actorName(aggregateId),
                            new State.NotCreated(aggregateId),
                            State::applyEvent
                    ).withPersistenceMode(persistenceMode)
                    .withSnapshotCriteria(SnapshotCriteria.every(2))
                    .withRetentionCriteria(RetentionCriteria.ofSnapshotEvery(2))
                    .withMessageConverter(messageConverter);

            // Create PersistenceEffector using fromConfig
            ctx.getLog().debug("Using {} mode for {}", persistenceMode, aggregateId);
            return PersistenceEffector.fromConfig(
                    config,
                    (state, effector) -> {
                        ctx.getLog().debug("Handling state: {}", state);

                        // Call handler based on state
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
        });
    }

    /**
     * Handle not created state
     *
     * @param state    State
     * @param effector Effector
     * @param ctx      Actor context
     * @return Actor behavior
     */
    private static Behavior<BankAccountCommand> handleNotCreated(
            State.NotCreated state,
            PersistenceEffector<State, BankAccountEvent, BankAccountCommand> effector,
            ActorContext<BankAccountCommand> ctx) {
        ctx.getLog().debug("Handling NotCreated state for aggregate: {}", state.getAggregateId());

        // Define basic behavior
        return Behaviors.receive(BankAccountCommand.class)
                .onMessage(BankAccountCommand.Create.class, cmd -> {
                    ctx.getLog().debug("Received Create command: {}", cmd);
                    var result = BankAccount.create(cmd.aggregateId());
                    ctx.getLog().debug("Created BankAccount persisting event: {}", result.getEvent());

                    // Send response before persisting event
                    cmd.replyTo().tell(CreateReply.succeeded(cmd.aggregateId()));

                    // Persist event
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
     * Handle created state
     *
     * @param state    State
     * @param effector Effector
     * @param ctx      Actor context
     * @return Actor behavior
     */
    private static Behavior<BankAccountCommand> handleCreated(
            State.Created state,
            PersistenceEffector<State, BankAccountEvent, BankAccountCommand> effector,
            ActorContext<BankAccountCommand> ctx) {
        ctx.getLog().debug("Handling Created state for aggregate: {}, balance: {}",
                state.getAggregateId(), state.bankAccount().getBalance());

        // Define basic behavior first
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

                        // Send response first
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

                        // Send response first
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

        // Persist snapshot (after defining behavior)
        return effector.persistSnapshot(state, snapshot -> {
            ctx.getLog().debug("Snapshot persisted for state: {}", snapshot);
            return behavior;
        });
    }
}

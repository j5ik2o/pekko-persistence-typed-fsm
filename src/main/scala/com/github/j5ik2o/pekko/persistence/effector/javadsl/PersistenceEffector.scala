package com.github.j5ik2o.pekko.persistence.effector.javadsl

import org.apache.pekko.actor.typed.Behavior

import java.util
import java.util.function.Function

/**
 * Java API for PersistenceEffector.
 *
 * @param S
 *   State type
 * @param E
 *   Event type
 * @param M
 *   Message type
 */
trait PersistenceEffector[S, E, M] {

  /**
   * Persist a single event.
   *
   * @param event
   *   event to persist
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEvent(event: E, onPersisted: Function[E, Behavior[M]]): Behavior[M]

  /**
   * Persist multiple events.
   *
   * @param events
   *   events to persist
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEvents(
    events: util.List[E],
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M]

  /**
   * Persist a snapshot.
   *
   * @param snapshot
   *   snapshot to persist
   * @param onPersisted
   *   callback to be called after the snapshot is persisted
   * @return
   *   new behavior
   */
  def persistSnapshot(snapshot: S, onPersisted: Function[S, Behavior[M]]): Behavior[M] =
    persistSnapshot(snapshot, false, onPersisted)

  /**
   * Persist a snapshot with force option.
   *
   * @param snapshot
   *   snapshot to persist
   * @param force
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after the snapshot is persisted
   * @return
   *   new behavior
   */
  def persistSnapshot(
    snapshot: S,
    force: Boolean,
    onPersisted: Function[S, Behavior[M]]): Behavior[M]

  /**
   * Persist event with snapshot.
   *
   * @param event
   *   event to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEventWithSnapshot(
    event: E,
    snapshot: S,
    onPersisted: Function[E, Behavior[M]]): Behavior[M] =
    persistEventWithSnapshot(event, snapshot, false, onPersisted)

  /**
   * Persist event with snapshot and force option.
   *
   * @param event
   *   event to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param forceSnapshot
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after the event is persisted
   * @return
   *   new behavior
   */
  def persistEventWithSnapshot(
    event: E,
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: Function[E, Behavior[M]]): Behavior[M]

  /**
   * Persist multiple events with snapshot.
   *
   * @param events
   *   events to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEventsWithSnapshot(
    events: util.List[E],
    snapshot: S,
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M] =
    persistEventsWithSnapshot(events, snapshot, false, onPersisted)

  /**
   * Persist multiple events with snapshot and force option.
   *
   * @param events
   *   events to persist
   * @param snapshot
   *   current state to evaluate snapshot strategy
   * @param forceSnapshot
   *   if true, persist snapshot regardless of snapshot strategy
   * @param onPersisted
   *   callback to be called after all events are persisted
   * @return
   *   new behavior
   */
  def persistEventsWithSnapshot(
    events: util.List[E],
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: Function[util.List[E], Behavior[M]]): Behavior[M]
}

package com.github.j5ik2o.pekko.persistence.effector.internal.javaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceEffector as ScalaDPE
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceEffector
import org.apache.pekko.actor.typed.Behavior

import scala.jdk.CollectionConverters.*

/**
 * Factory object for creating PersistenceEffectorWrapper instances.
 */
private[effector] object PersistenceEffectorWrapper {

  /**
   * Create a new PersistenceEffectorWrapper that wraps a Scala PersistenceEffector.
   *
   * @param underlying
   *   The Scala PersistenceEffector to wrap
   * @tparam S
   *   Type of state
   * @tparam E
   *   Type of event
   * @tparam M
   *   Type of message
   * @return
   *   A Java-compatible PersistenceEffector
   */
  def create[S, E, M](
    underlying: ScalaDPE[S, E, M],
  ): PersistenceEffector[S, E, M] = new PersistenceEffectorWrapper(underlying)

}

/**
 * Wrapper class that adapts a Scala PersistenceEffector to the Java API. This class converts
 * between Scala and Java types and function interfaces.
 *
 * @param underlying
 *   The Scala PersistenceEffector to wrap
 * @tparam S
 *   Type of state
 * @tparam E
 *   Type of event
 * @tparam M
 *   Type of message
 */
private[effector] final class PersistenceEffectorWrapper[S, E, M] private (
  underlying: ScalaDPE[S, E, M],
) extends PersistenceEffector[S, E, M] {

  override def persistEvent(
    event: E,
    onPersisted: java.util.function.Function[E, Behavior[M]]): Behavior[M] =
    underlying.persistEvent(event)(e => onPersisted.apply(e))

  override def persistEvents(
    events: java.util.List[E],
    onPersisted: java.util.function.Function[java.util.List[E], Behavior[M]]): Behavior[M] =
    underlying.persistEvents(events.asScala.toSeq) { es =>
      onPersisted.apply(es.asJava)
    }

  override def persistSnapshot(
    snapshot: S,
    force: Boolean,
    onPersisted: java.util.function.Function[S, Behavior[M]]): Behavior[M] =
    underlying.persistSnapshot(snapshot, force)(s => onPersisted.apply(s))

  override def persistEventWithSnapshot(
    event: E,
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: java.util.function.Function[E, Behavior[M]]): Behavior[M] =
    underlying.persistEventWithSnapshot(event, snapshot, forceSnapshot)(e => onPersisted.apply(e))

  override def persistEventsWithSnapshot(
    events: java.util.List[E],
    snapshot: S,
    forceSnapshot: Boolean,
    onPersisted: java.util.function.Function[java.util.List[E], Behavior[M]]): Behavior[M] =
    underlying.persistEventsWithSnapshot(events.asScala.toSeq, snapshot, forceSnapshot) { es =>
      onPersisted.apply(es.asJava)
    }
}

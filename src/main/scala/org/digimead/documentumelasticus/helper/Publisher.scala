/*
 *
 * This file is part of the Documentum Elasticus project.
 * Copyright (c) 2010-2011 Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»
 * Author: Alexey Aksenov
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
 * BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
 * THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using Documentum Elasticus.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Documentum Elasticus software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping Documentum Elasticus with a closed source product.
 *
 * For more information, please contact Documentum Elasticus Team at this
 * address: ezh@ezh.msk.ru
 *
 */

package org.digimead.documentumelasticus.helper

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.Subscriber
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.SynchronizedSet
import scala.collection.mutable.{ Publisher => ScalaPublisher }

trait SubscribeSelf[E] extends Publisher[E] {
  def notify(event: E)
  override protected def publish(event: E) {
    super.publish(event)
    notify(event)
  }
}

trait Publisher[E] extends ScalaPublisher[E] {
  implicit def eventToEventMatcher(event: E): PartialFunction[E, Unit] = {
    log.trace("wait for " + event);
    { case e if (e == event) => log.debug("wait for " + event + " completed") }
  }
  implicit def seqEventToEventMatcher(events: Seq[E]): PartialFunction[E, Unit] = {
    log.trace("wait for " + events.mkString(","));
    { case e if (events.exists(ev => { ev == e })) => log.debug("wait for " + events.mkString(",") + " completed") }
  }
  val log: Logger
  private val that = this
  private val waiter = new HashMap[PartialFunction[E, Unit], HashSet[AnyRef]] with SynchronizedMap[PartialFunction[E, Unit], HashSet[AnyRef]]
  private val waiterGuard = new HashSet[AnyRef] with SynchronizedSet[AnyRef]
  val history = new HashSet[E] with SynchronizedSet[E]
  // case class
  private val historyRulesInstance = new HashMap[E, (HashSet[E]) => Unit] with SynchronizedMap[E, (HashSet[E]) => Unit]
  // classOf[case class]
  private val historyRulesClass = new HashMap[Class[_ <: E], (HashSet[E]) => Unit] with SynchronizedMap[Class[_ <: E], (HashSet[E]) => Unit]
  def addRule(e: E, block: (HashSet[E]) => Unit) = historyRulesInstance(e) = block
  def addRule(e: Class[_ <: E], block: (HashSet[E]) => Unit) = historyRulesClass(e) = block
  /*
   * clear
   */
  def clearEvent(event: E): Unit = clearEvent(Seq(event))
  def clearEvent(event: Seq[E]): Unit = event.foreach(e => history.removeEntry(e))
  def clearEvent(): Unit = history.clear()
  /*
   * wait()
   */
  // wait event E
  def waitEvent(eventMatcher: PartialFunction[E, Unit]): Unit = waitEvent(eventMatcher, false, new Object)
  def waitEvent(eventMatcher: PartialFunction[E, Unit], ref: AnyRef): Unit = waitEvent(eventMatcher, false, ref)
  // wait event E with history flag
  def waitEvent(eventMatcher: PartialFunction[E, Unit], checkHistory: Boolean): Unit = waitEvent(eventMatcher, checkHistory, new Object)
  def waitEvent(eventMatcher: PartialFunction[E, Unit], checkHistory: Boolean, ref: AnyRef): Unit = waitEvent(eventMatcher,
    ref, () => ref.synchronized {
      do {
        ref.wait()
      } while (!waiterGuard(ref))
      waiterGuard(ref) = false
      val w = getWaiter(eventMatcher)
      w.removeEntry(ref)
      if (w.isEmpty)
        waiter.remove(eventMatcher)
    }, checkHistory)

  /*
   * wait(Long)
   */
  // wait event E
  def waitEvent(eventMatcher: PartialFunction[E, Unit], timeout: Long): Unit = waitEvent(eventMatcher, timeout, false, new Object)
  def waitEvent(eventMatcher: PartialFunction[E, Unit], timeout: Long, ref: AnyRef): Unit = waitEvent(eventMatcher, timeout, false, ref)
  // wait event E with history flag
  def waitEvent(eventMatcher: PartialFunction[E, Unit], timeout: Long, checkHistory: Boolean): Unit = waitEvent(eventMatcher, timeout, checkHistory, new Object)
  def waitEvent(eventMatcher: PartialFunction[E, Unit], timeout: Long, checkHistory: Boolean, ref: AnyRef): Unit = waitEvent(eventMatcher,
    ref, () => ref.synchronized {
      val ts = System.currentTimeMillis()
      do {
        ref.wait(timeout)
        if ((ts + timeout) < System.currentTimeMillis()) {
          log.debug("timeout for matcher with monitor " + ref)
          waiterGuard(ref) = true
        }
      } while (!waiterGuard(ref))
      waiterGuard(ref) = false
      val w = getWaiter(eventMatcher)
      w.removeEntry(ref)
      if (w.isEmpty)
        waiter.remove(eventMatcher)
    }, checkHistory)
  /*
   * wait(Long, Int)
   */
  //def waitEvent(event: E, ref: AnyRef, timeout: Long, nanos: Int): Unit = waitEvent(event, ref, () => ref.synchronized { ref.wait(timeout, nanos) }, false)
  //def waitEvent(event: E, ref: AnyRef, timeout: Long, nanos: Int, checkHistory: Boolean): Unit = waitEvent(event, ref, () => ref.synchronized { ref.wait(timeout, nanos) }, checkHistory)*/
  private def waitEvent(eventMatcher: PartialFunction[E, Unit], ref: AnyRef, block: () => Any, checkHistory: Boolean): Unit = {
    log.trace("wait matcher with monitor " + ref + ", check history " + checkHistory)
    if (checkHistory && history.exists(h => {
      if (eventMatcher.isDefinedAt(h)) {
        eventMatcher(h)
        true
      } else {
        false
      }
    })) {
      log.trace("wait completed (by history lookup) for matcher with monitor " + ref)
    } else {
      addWaiter(eventMatcher, ref)
      block()
      log.trace("wait completed for matcher with monitor " + ref)
    }
  }
  private def addWaiter(eventMatcher: PartialFunction[E, Unit], ref: AnyRef) = synchronized {
    if (!waiter.isDefinedAt(eventMatcher))
      waiter(eventMatcher) = new HashSet[AnyRef] with SynchronizedSet[AnyRef]
    if (!waiter(eventMatcher).addEntry(ref))
      throw new RuntimeException("try to add event " + eventMatcher + " waiter but there is already one")
  }
  private def getWaiter(eventMatcher: PartialFunction[E, Unit]) = waiter.getOrElse(eventMatcher, new HashSet[AnyRef] with SynchronizedSet[AnyRef])
  override protected def publish(event: E) {
    log.trace("publish " + event)
    history(event) = true
    if (historyRulesInstance.isDefinedAt(event))
      historyRulesInstance(event)(history)
    if (historyRulesClass.isDefinedAt(event.getClass()))
      historyRulesClass(event.getClass())(history)
    super.publish(event)
    waiter.foreach(e => {
      val matcher = e._1
      val refs = e._2
      if (matcher.isDefinedAt(event)) {
        matcher(event)
        refs.foreach(ref => ref.synchronized {
          waiterGuard(ref) = true
          ref.notifyAll()
        })
      }
    })
  }
}

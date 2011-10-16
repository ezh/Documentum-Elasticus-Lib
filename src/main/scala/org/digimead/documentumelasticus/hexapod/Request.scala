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

package org.digimead.documentumelasticus.hexapod

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import org.digimead.documentumelasticus.hexapod.bot.Bot
import org.slf4j.{Logger, LoggerFactory, MDC}
import scala.actors.Actor
import scala.actors.Futures.future
import scala.collection.mutable.{HashMap, SynchronizedMap}

/*
 * Request(target, bot, Ping, key) onSuccess { ... } onFail { ... } setTimeout(...) send
 * Request(target, bot, Msg("some"), key) onSuccess { ... } onFail { ... } wait[Any]
 * Request(target, bot, Blk("some", 3), key) onSuccess { ... } onFail { ... } send
 *
 * RuntimeException in onSuccess fire onFail callback
 */

protected[this] sealed trait RequestType {
  val message: String
}

sealed trait RequestEvent
sealed trait RequestMessage

class Request(val target: String, val bot: Bot, val t: RequestType, val key: String) {
  private var log = LoggerFactory.getLogger(getClass)
  private val botType = bot.getClass.getName.split("\\.").last
  private val retry = new AtomicInteger(0)
  val force = new AtomicBoolean(false)
  private val timeoutWatcher = new AtomicReference[ScheduledExecutorService](null)
  private var timeout = Request.maximumTimeout
  private var success: (String) => Any = (str) => (str)
  private val successWrapper = (str: String) => result.synchronized {
    MDC.put("key", key)
    MDC.put("target", target)
    MDC.put("bot", bot.toString())
    t match {
      case Request.Message.Ping => log.trace("[SENDPING] successful for " + botType + " " + target)
      case Request.Message.Simple(message) => log.trace("[SENDSIMPLE] successful for " + botType + " " + target + " " + message)
      case Request.Message.Complex(message, limit) => log.trace("[SENDCOMPLEX] successful for " + botType + " " + target + " " + message)
    }
    val r = try {
      success(str) match {
        // return Option, not Option[Option[...]]
        case r: Option[_] => r
        case r => Some(r)
      }
    } catch {
      // return null
      case _: RuntimeException => null
    }
    if (r != null) {
      // ok
      result.set(r)
      if (timeoutWatcher.get != null)
        timeoutWatcher.get.shutdownNow
      if (Request.requestMap.isDefinedAt(key)) {
        (Request.requestMap(key).filterNot(_ == this)) match {
          case Nil =>
            log.trace("remove key " + key)
            Request.requestMap.remove(key)
          case list =>
            log.trace("remove request from key " + key)
            Request.requestMap(key) = list
        }
      }
      result.notifyAll
    } else {
      // fail (RuntimeException)
      failWrapper()
    }
    MDC.remove("key")
    MDC.remove("target")
    MDC.remove("bot")
  }
  private var fail: () => Any = () => None
  private val failWrapper = () => result.synchronized {
    MDC.put("key", key)
    MDC.put("target", target)
    MDC.put("bot", bot.toString())
    t match {
      case Request.Message.Ping => log.trace("[SENDPING] failed for " + botType + " " + target)
      case Request.Message.Simple(message) => log.trace("[SENDSIMPLE] failed for " + botType + " " + target + " " + message)
      case Request.Message.Complex(message, limit) => log.trace("[SENDCOMPLEX] failed for " + botType + " " + target + " " + message)
    }
    if (retry.getAndDecrement > 0) {
      log.warn("request " + t + " failed, retry " + retry.get)
      send()
    } else {
      log.warn("request " + t + " failed")
      val r = fail() match {
        // return Option, not Option[Option[...]]
        case r: Option[_] => r
        case r => Some(r)
      }
      result.set(r)
      if (timeoutWatcher.get != null)
        timeoutWatcher.get.shutdownNow
      if (Request.requestMap.isDefinedAt(key)) {
        (Request.requestMap(key).filterNot(_ == this)) match {
          case Nil =>
            log.trace("remove key " + key)
            Request.requestMap.remove(key)
          case list =>
            log.trace("remove request from key " + key)
            Request.requestMap(key) = list
        }
      }
      result.notifyAll
    }
    MDC.remove("key")
    MDC.remove("target")
    MDC.remove("bot")
  }
  val result = new AtomicReference[Option[Any]](null)
  def onSuccess[T](block: String => T): Request = synchronized {
    success = block
    this
  }
  def onFail(block: => Any): Request = synchronized {
    fail = () => { block }
    this
  }
  def setRetry(value: Int) = {
    assert(value > 0)
    retry.set(value)
    this
  }
  def setForce(value: Boolean) = {
    force.set(value)
    this
  }
  def setLogger(value: Logger) = {
    assert(value != null)
    log = value
    this
  }
  def setTimeoutSeconds(value: Int) = setTimeout(value * 1000)
  def setTimeout(value: Int) = {
    timeout = value
    this
  }
  def send(): Unit = {
    MDC.put("key", key)
    MDC.put("target", target)
    MDC.put("bot", bot.toString())
    t match {
      case Request.Message.Ping =>
        log.trace("[SENDPING] to " + botType + " " + target + " with timeout " + timeout)
      case Request.Message.Simple(message) =>
        log.trace("[SENDSIMPLE] to " + botType + " " + target + " " + message + " with timeout " + timeout)
      case Request.Message.Complex(message, limit) =>
        log.trace("[SENDCOMPLEX] to " + botType + " " + target + " " + message + " with timeout " + timeout)
    }
    bot ! this
    if (timeoutWatcher.get != null)
      timeoutWatcher.get.shutdownNow
    timeoutWatcher.set(Executors.newSingleThreadScheduledExecutor())
    timeoutWatcher.get.schedule(new Runnable { def run = failWrapper() }, timeout, TimeUnit.MILLISECONDS)
    MDC.remove("key")
    MDC.remove("target")
    MDC.remove("bot")
  }
  def send[T <% Option[_]]: T = {
    MDC.put("key", key)
    MDC.put("target", target)
    MDC.put("bot", bot.toString())
    send()
    log.trace("wait for " + t + " from " + target + " with key " + key)
    result.synchronized {
      while (result.get == null)
        if (timeout != 0) {
          result.wait(timeout)
          if (result.get == null)
            result.set(
              fail() match {
                case r: Option[_] => r
                case r => Some(r)
              })
        } else {
          result.wait
        }
    }
    val r = try {
      val r = result.get.asInstanceOf[T]
      log.trace("wait complete for " + t + " from " + target + ", result " + r)
      result.set(null)
      r
    } catch {
      case e =>
        log.error(e.getMessage(), e)
        throw e
    }
    MDC.remove("key")
    MDC.remove("target")
    MDC.remove("bot")
    r
  }
}

object Request extends Actor {
  val log = LoggerFactory.getLogger(this.getClass)
  private val maximumTimeout = 5 * 60 * 1000
  private var requestMap = new HashMap[String, Seq[Request]] with SynchronizedMap[String, Seq[Request]]
  start
  def apply(target: String, bot: Bot, t: RequestType) = {
    val key = bot.hashCode.toString + "." + (target.hashCode + t.message.hashCode).toString
    val request = new Request(target, bot, t, key)
    log.debug("request " + t.message + " from " + target + ", target hash " + target.hashCode + ", message hash " + t.message.hashCode + ", key " + key)
    if (requestMap.isDefinedAt(key))
      requestMap(key) = requestMap(key) :+ request
    else
      requestMap(key) = Seq(request)
    request
  }
  def act {
    loop {
      react {
        case response: Reply =>
          future {
            try {
              val key = response.botHash.toString + "." + response.blockHash.toString
              if (requestMap.isDefinedAt(key)) {
                log.trace("process message " + response.message + " with key " + key)
                requestMap(key).foreach(_.successWrapper(response.message))
              } else {
                log.trace("skip message " + response.message + " with key " + key)
              }
            } catch { case e => log.error(e.getMessage, e) }
          }
        case message =>
          log.error("unknow message" + message)
      }
    }
  }
  object Message {
    case object Ping extends RequestType { val message = "ping" }
    case class Simple(val message: String) extends RequestType
    case class Complex(val message: String, limit: Int = 0) extends RequestType // limit messages
  }
}

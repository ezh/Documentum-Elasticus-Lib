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

package org.digimead.documentumelasticus.hexapod.archinid
import de.javawi.jstun.util.Address
import de.javawi.jstun.util.Address

import java.util.UUID
import java.util.concurrent.{ Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import org.digimead.documentumelasticus.hexapod.Hexapod
import org.digimead.documentumelasticus.hexapod.Info
import org.digimead.documentumelasticus.hexapod.InfoAkka
import org.digimead.documentumelasticus.hexapod.InfoIRC
import org.digimead.documentumelasticus.hexapod.InfoJabber
import org.digimead.documentumelasticus.hexapod.PoolSingleton
import org.digimead.documentumelasticus.hexapod.Request
import org.digimead.documentumelasticus.hexapod.bot.Bot
import org.digimead.documentumelasticus.hexapod.bot.BotEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.actors.Actor
import scala.actors.Futures._
import scala.actors.threadpool.AtomicInteger
import scala.collection.mutable.{ ArrayBuffer, HashMap, Subscriber, SynchronizedBuffer }

class Archinid(uuid: UUID, _publicIP: Address, _consolePort: Int, Pool: PoolSingleton) extends Hexapod(uuid, "archinid", LoggerFactory.getLogger(classOf[Archinid].getName)) {
  override protected def initialize: AnyRef = new InstanceA(Some(_publicIP), _consolePort match {
    case n if (n > 0 && n <= 65535) => Some(n)
    case _ => None
  })
  class InstanceA(publicIP: Option[Address], consolePort: Option[Int]) extends super.InstanceH(uuid, publicIP, consolePort, kind, log, this) with Actor {
    val prio: Array[String] = Array("akka", "jabber", "irc")
    var id: Seq[String] = Seq()
    var lastSeen: Long = 0
    val initDelay = 500 to 3000 // ms
    val checkArchinidBotLock: HashMap[Int, ReentrantLock] = new HashMap()
    val beatTimeout = 60 * 60 * 1000
    val beatScheduler = schedule(initDelay(scala.util.Random.nextInt(initDelay length)), beatTimeout, { this ! Archinid.Message.Heartbeat })
    start()
    init()
    log.info("active " + uuid)
    def components(option: String, timeoutSeconds: Int = -1): Seq[Component] = {
      log.debug("get components [" + option + "] with timeout " + timeoutSeconds + "s")
      val a = bestEntity.map(entity => Archinid.components(entity.bot, entity.target, option, timeoutSeconds))
      /*match {
	case Some(n) => n
	case None => Seq()
	}*/
      null
    }
    def status(timeout: Int = 60000): Option[Info] = {
      log.debug("get status with timeout " + timeout)
      var result: Option[Info] = null
      var counter = 1
      // TODO
      result
    }
    def neighbors(timeout: Int = 60000): Seq[Info] = {
      log.debug("get neighbors with timeout " + timeout)
      var result: Seq[Info] = null
      var counter = 1
      // TODO
      result
    }
    def act() {
      loop {
        react {
          case Archinid.Message.Heartbeat =>
            try { heartbeat() } catch { case e => log.error(e.getMessage, e) }
          case message =>
            log.error("unknow message" + message)
        }
      }
    }
    private def heartbeat() = synchronized { entity.values.foreach(e => future { checkArchinidBotState(e) }) }
    private def schedule(delay: Int, timeout: Int, block: => Any) = {
      val scheduler = Executors.newSingleThreadScheduledExecutor()
      val runnable = new Runnable { def run { block } }
      scheduler.scheduleAtFixedRate(runnable, delay, timeout, TimeUnit.MILLISECONDS)
      scheduler
    }
    private def init() {
      entity.values.foreach(entity => {
        val subscriber = new Subscriber[BotEvent, entity.bot.Pub] {
          def notify(pub: entity.bot.Pub, event: BotEvent): Unit = {
            event match {
              case Bot.Event.Connected =>
                log.debug("bot " + entity.bot + " connected")
                future {
                  val delay = initDelay(scala.util.Random.nextInt(initDelay length))
                  Thread.sleep(delay)
                  future { checkArchinidBotState(entity) }
                }
              case Bot.Event.Disconnected =>
                log.debug("bot " + entity.bot + " disconnected")
                entity.health = false
              case Bot.Event.UserEnter(target) =>
                log.debug("user " + target + " enter")
              case Bot.Event.UserLeave(target) =>
                log.debug("user " + target + " leave")
              case Bot.Event.Connecting(timestamp) =>
              case _ =>
            }
          }
        }
        entity.bot.subscribe(subscriber)
      })
      Pool.addNode(box.asInstanceOf[Archinid])
    }
    override def dispose(replaceBy: Hexapod = null) {
      log.info("dispose archinid " + uuid)
      super.dispose(replaceBy)
      Pool.delNode(uuid)
    }
    private def checkArchinidBotState(entity: BotEntity) {
      val hash = entity.bot.hashCode()
      if ((beatTimeout * 3 > (System.currentTimeMillis() - entity.lastActivity)) || entity.health == false)
        return
      if (!checkArchinidBotLock.isDefinedAt(hash))
        checkArchinidBotLock(hash) = new ReentrantLock
      if (checkArchinidBotLock(hash).tryLock) {
        try {
          val result: Boolean = Archinid.ping(entity.bot, entity.target)
          if (entity.health != result) {
            log.debug("change " + entity.bot.getClass.getName.split("\\.").last + " bot state to " + result + " for " + entity.target)
            entity.health = result
          }
        } catch {
          case e => log.error(e.getMessage, e)
        } finally {
          checkArchinidBotLock(hash).unlock()
        }
      }
    }
  }
}

object Archinid {
  implicit def Instance2Inner(o: Archinid): Archinid#InstanceA = if (o.instance != null) {
    o.instance.asInstanceOf[Archinid#InstanceA]
  } else {
    val message = "Archinid instance " + o + " disposed"
    log.error(message)
    throw new IllegalStateException(message)
  }
  val log = LoggerFactory.getLogger(this.getClass)
  private val responseTimeout = 1000 // 2s
  def ping(bot: Bot, target: String, timeoutSeconds: Int = -1, retry: Int = 3): Boolean = {
    log.debug("ping target " + target + " with timeout " + timeoutSeconds + "s")
    Request(target, bot, Request.Message.Ping).
      setTimeoutSeconds(if (timeoutSeconds < 0) (bot.timeout / 1000) else timeoutSeconds).
      setLogger(log).
      setRetry(retry).
      send[Option[Any]] match {
        case Some(_) => true
        case None => false
      }
  }
  def components(bot: Bot, target: String, option: String, timeoutSeconds: Int = -1): Seq[Component] = {
    log.debug("get components [" + option + "] with timeout " + timeoutSeconds + "s from " + target)
    val componentEntry = """"(Documentum-Elasticus-\S+)", .*""".r
    val requestComponent = Request(target, bot, Request.Message.Simple("components " + option))
    requestComponent.onSuccess(str => {
      if (str == "") {
        log.debug("empty components block from " + target)
        throw new RuntimeException // switch to onFail
      } else {
        str.split("\n").par.map(str => {
          val componentEntry(componentName) = str
          log.debug("request information about \"" + componentName + "\" component")
          val requestDetails = Request(target, bot, Request.Message.Simple("component " + componentName))
          requestDetails.onSuccess(str => {
            if (str == "") {
              log.debug("empty details block from " + target)
              throw new RuntimeException // switch to onFail
            } else {
              if (str.split("\n")(0) != "NAME: " + componentName) {
                log.warn("skip component " + componentName)
                throw new RuntimeException // switch to onFail
              } else {
                componentsParseDetails(str)
              }
            }
          })
          requestDetails.setRetry(3)
          requestDetails.setTimeoutSeconds(if (timeoutSeconds < 0) (bot.timeout / 1000) * 3 else timeoutSeconds)
          requestDetails.setLogger(log)
          requestDetails.send[Option[Component]].getOrElse(null)
        }).filter(_ != null).seq
      }
    })
    requestComponent.setRetry(3)
    requestComponent.setTimeoutSeconds(if (timeoutSeconds < 0) (bot.timeout / 1000) * 3 else timeoutSeconds)
    requestComponent.setLogger(log)
    requestComponent.send[Option[Seq[Component]]].getOrElse(Seq())
  }
  private def componentsParseDetails(block: String): Option[Component] = {
    log.debug("parse component information")
    val replyMap: HashMap[String, Any] = HashMap()
    var versions: Map[String, Map[String, String]] = Map()
    var currentVersion: Tuple3[String, String, String] = Tuple3("", "", "") // name, id, url
    var currentVersionMap: Map[String, String] = Map()
    block.split("\n").foreach(str => {
      val versionRegEx = """VERSION: (.*) \(([a-f0-9]+)\) at (.*)""".r
      val fileRegEx = """FILE: "(.*)" -> "(.*)"$""".r
      str match {
        case str if str.startsWith("NAME: ") => replyMap.update("name", str.substring(6))
        case str if str.startsWith("DESCRIPTION: ") => replyMap.update("description", str.substring(13))
        case str if str.startsWith("URL: ") => replyMap.update("url", str.substring(5))
        case str if str.startsWith("HOMEPAGE: ") => replyMap.update("homepage", str.substring(10))
        case str if str.startsWith("PUBLISHED: ") => replyMap.update("created_at", str.substring(11))
        case str if str.startsWith("UPDATED: ") => replyMap.update("pushed_at", str.substring(9))
        case str if str.startsWith("WATCHERS: ") => replyMap.update("watchers", str.substring(10))
        case str if str.startsWith("FORKS: ") => replyMap.update("forks", str.substring(7))
        case str if str.startsWith("OPEN_ISSUES: ") => replyMap.update("open_issues", str.substring(13))
        case str if str.startsWith("VERSION: ") =>
          if (currentVersion._1 != "")
            versions = versions.updated(currentVersion._1, currentVersionMap)
          val versionRegEx(pName, pID, pURL) = str
          currentVersion = Tuple3(pName, pID, pURL)
          currentVersionMap = Map()
        case str if str.startsWith("FILE: ") =>
          if (currentVersion._1 == "") {
            log.error("file not binded to any version: " + str)
          } else {
            val fileRegEx(fileName, fileURL) = str
            currentVersionMap = currentVersionMap.updated(fileName, fileURL)
          }
        case str => log.error("unknown string: " + str)
      }
    })
    if (currentVersion != "")
      versions = versions.updated(currentVersion._1, currentVersionMap)
    // instantiate component
    Some(new Component(replyMap.getOrElse("url", "http://").asInstanceOf[String],
      replyMap.getOrElse("homepage", "http://").asInstanceOf[String],
      replyMap.getOrElse("watchers", "0").asInstanceOf[String].toInt,
      replyMap.getOrElse("created_at", "lost").asInstanceOf[String],
      replyMap.getOrElse("pushed_at", "lost").asInstanceOf[String],
      replyMap.getOrElse("forks", "0").asInstanceOf[String].toInt,
      replyMap.getOrElse("name", "lost").asInstanceOf[String],
      replyMap.getOrElse("description", "lost").asInstanceOf[String],
      replyMap.getOrElse("open_issues", "0").asInstanceOf[String].toInt,
      null))
  }
  def status(bot: Bot, target: String, timeoutSeconds: Int = -1): Option[Info] = {
    log.debug("get status with timeout " + timeoutSeconds + "s")
    val requestStatus = Request(target, bot, Request.Message.Simple("status"))
    requestStatus.onSuccess(str => {
      if (str == "") {
        log.debug("empty status block from " + target)
        throw new RuntimeException // switch to onFail
      } else {
        val buffer = str.split("\n")
        if (!buffer(0).matches("""([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) at (\d+\.\d+\.\d+\.\d+):(-?\d+).*""")) {
          log.debug("skip broken status block from " + target)
          None
        } else {
          val rHead = """([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) at (\d+\.\d+\.\d+\.\d+):(-?\d+).*""".r
          val rHead(uuid, ip, consolePort) = buffer(0)
          val rIRC = """IRC: nick "(.*)" server "(.*)" channels "(.*)"""".r
          val irc: Seq[InfoIRC] = buffer.filter(_.startsWith("IRC: ")).map(str => {
            val rIRC(nick, server, channels) = str
            InfoIRC(nick, server, channels.split(",").map(_.trim()))
          })
          val rJabber = """Jabber: nick "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})" server "(.*)"""".r
          val jabber: Seq[InfoJabber] = buffer.filter(_.startsWith("Jabber: ")).map(str => {
            val rJabber(nick, server) = str
            InfoJabber(nick, server)
          })
          // TODO parse akka
          val akka: Seq[InfoAkka] = Seq()
          val uuidParsed = UUID.fromString(uuid)
          Info(uuidParsed, new Address(ip), consolePort.toInt, irc, jabber, akka)
        }
      }
    })
    requestStatus.setRetry(3)
    requestStatus.setTimeoutSeconds(if (timeoutSeconds < 0) (bot.timeout / 1000) * 3 else timeoutSeconds)
    requestStatus.setLogger(log)
    requestStatus.send[Option[Info]]
  }
  def neighbors(bot: Bot, target: String, timeout: Int = 60000): Seq[Info] = {
    log.debug("get neighbors with timeout " + timeout + " from " + target)
    /*    var neighbors: Seq[String] = null
    for (i <- 0 to 3 if neighbors == null) {
      if (i != 0) log.warn("retry " + i)
      neighbors = bot !? Bot.Message.RequestEx(target, "neighbors", timeout) match {
        case Some(c: Seq[_]) => c.asInstanceOf[Seq[String]]
        case x => log.warn("broken neighbors block, reply: " + x); null
      }
    }
    if (neighbors == null) {
      log.warn("something wrone with " + target)
      null
    } else if (neighbors.isEmpty) {
      log.debug("empty neighbors block from " + target)
      Seq()
    } else {
      neighbors.map(neighbor => {
        val buffer = neighbor.split("\n")
        if (!buffer(0).matches("""([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) at (\d+\.\d+\.\d+\.\d+).*""")) {
          log.debug("skip broken neighbors block from " + target)
          null
        } else {
          val rHead = """([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) at (\d+\.\d+\.\d+\.\d+).*""".r
          val rHead(uuid, ip) = buffer(0)
          val rIRC = """IRC: nick "(.*)" server "(.*)" channels "(.*)"""".r
          val irc: Seq[InfoIRC] = buffer.filter(_.startsWith("IRC: ")).map(str => {
            val rIRC(nick, server, channels) = str
            InfoIRC(nick, server, channels.split(",").map(_.trim()))
          })
          val rJabber = """Jabber: nick "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})" server "(.*)"""".r
          val jabber: Seq[InfoJabber] = buffer.filter(_.startsWith("Jabber: ")).map(str => {
            val rJabber(nick, server) = str
            InfoJabber(nick, server)
          })
          // TODO parse akka
          val akka: Seq[InfoAkka] = Seq()
          val uuidParsed = UUID.fromString(uuid)
          Info(uuidParsed, ip, irc, jabber, akka)
        }
      }).filter(_ != null)
      }*/
    Seq()
  }
  object Message {
    case object Heartbeat
  }
}

/*
 update
    log.debug("update " + uuid.toString() + "\n" +
      "IRC: " + irc.map(i => { i._1 + "@" + i._2.serverUri }).mkString(", ") + "\n" +
      "Jabber: " + jabber.map(i => { i._1 + "@" + i._2.serverUri }).mkString(", ") + "\n" +
      "Akka: " + akka.map(i => { i._1 }).mkString(", "))
*/
/*
 components
    for (n <- prio if result == null) {
      try {
        val method = this.getClass().getMethod(n)
        val bots = method.invoke(this).asInstanceOf[Seq[Tuple2[String, Bot]]]
        for (b <- bots if result == null) {
          log.debug("request no. " + counter + " to " + b._2.getClass().getName() + " " + b._1)
          result = Archinid.components(b._2, b._1, option, timeout)
          counter += 1
        }
      } catch {
        case e => log.warn(e.getMessage, e)
      }
    }
*/
/*
 status
    for (n <- prio if result == null) {
      try {
        val method = this.getClass().getMethod(n)
        val bots = method.invoke(this).asInstanceOf[Seq[Tuple2[String, Bot]]]
        for (b <- bots if result == null) {
          log.debug("request no. " + counter + " to " + b._2.getClass().getName() + " " + b._1)
          result = Archinid.status(b._2, b._1, timeout)
          counter += 1
        }
      } catch {
        case e => log.warn(e.getMessage, e)
      }
    }
*/
/*
     for (n <- prio if result == null) {
      try {
        val method = this.getClass().getMethod(n)
        val bots = method.invoke(this).asInstanceOf[Seq[Tuple2[String, Bot]]]
        for (b <- bots if result == null) {
          log.debug("request no. " + counter + " to " + b._2.getClass().getName() + " " + b._1)
          result = Archinid.neighbors(b._2, b._1)
          counter += 1
        }
      } catch {
        case e => log.warn(e.getMessage, e)
      }
    }

*/
/*
  def components(option: String, timeout: Int): Seq[Component] = {
    log.debug("get components [" + option + "] with timeout " + timeout)
    val startT = System.currentTimeMillis
    val endT = startT + timeout
    val node = nodeMap.values.toSeq.sortWith(_.lastSeen > _.lastSeen)
    if (node.size < 1) {
      log.warn("components unreachable N:" + node.size)
      return Seq()
    }
    node.head.components(option, (endT - System.currentTimeMillis).toInt)
  }
  def status(target: String, timeout: Int = 60000): Option[Info] = {
    log.debug("get status for " + target + " with timeout " + timeout)
    val startT = System.currentTimeMillis
    val endT = startT + timeout
    val node = nodeMap.values.filter(a => { a.id.exists(_ == target) })
    if (node.size != 1) {
      log.warn("target " + target + " unreachable N:" + node.size)
      return None
    }
    node.head.status((endT - System.currentTimeMillis).toInt)
  }
  def neighbors(target: String, timeout: Int = 60000): Seq[Info] = {
    log.debug("get neighbors for " + target + " with timeout " + timeout)
    val startT = System.currentTimeMillis
    val endT = startT + timeout
    val node = nodeMap.values.filter(a => { a.id.exists(_ == target) })
    if (node.size != 1) {
      log.warn("target " + target + " unreachable N:" + node.size)
      return Seq()
    }
    node.head.neighbors((endT - System.currentTimeMillis).toInt)
  }
*/

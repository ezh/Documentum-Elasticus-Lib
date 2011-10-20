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
import de.javawi.jstun.util.Address

import java.io.{ File, FileWriter, PrintWriter }
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong }
import org.digimead.documentumelasticus.helper.Config
import org.digimead.documentumelasticus.helper.Publisher
import org.digimead.documentumelasticus.helper.SubscribeSelf
import org.digimead.documentumelasticus.helper.openpgp.OpenPGP
import org.digimead.documentumelasticus.helper.openpgp.{ KeySet, Locked, Unlocked }
import org.digimead.documentumelasticus.hexapod.bot.Bot
import org.digimead.documentumelasticus.hexapod.bot.BotEvent
import org.slf4j.{ Logger, LoggerFactory }
import scala.actors.Futures.future
import scala.actors.threadpool.AtomicInteger
import scala.collection.mutable.{ HashMap, ObservableMap, Subscriber, SynchronizedMap, Undoable }
//import scala.collection.script.Message
import scala.io.BufferedSource

sealed trait HexapodEvent
case class HexapodChanged(reason: String, uuid: UUID, kind: String, bot: Bot)

class Hexapod(val uuid: UUID, val kind: String = "hexapod", val log: Logger = LoggerFactory.getLogger(getClass.getName)) {
  protected var instance: AnyRef = initialize
  Hexapod.publishFromCompanion(Hexapod.Event.New(this))
  protected def initialize: AnyRef = new InstanceH(uuid, None, None, kind, log, this)
  class InstanceH(val uuid: UUID, var publicIP: Option[Address], var consolePort: Option[Int], val kind: String, val log: Logger, val box: Hexapod) extends Publisher[HexapodChanged] {
    val entity = new HashMap[Bot, BotEntity] with SynchronizedMap[Bot, BotEntity]
    private var subscribers = Seq[Tuple2[Bot, Subscriber[_, _]]]()
    if (Hexapod.hexapod.isDefinedAt(uuid)) {
      Hexapod.hexapod(uuid).entity.foreach(t => {
        val subscriber = subscribeBot(t._1)
        entity(t._1) = BotEntity(publish _)(
          t._2.bot,
          t._2.target,
          subscriber,
          new AtomicInteger(t._2.priority),
          new AtomicBoolean(t._2.authenticated),
          new AtomicInteger(t._2.authenticationAttempt),
          new AtomicBoolean(t._2.health),
          new AtomicLong(t._2.lastActivity))
      })
      Hexapod.hexapod(uuid).dispose(box)
    }
    Hexapod.hexapod(uuid) = box
    log.info("alive " + kind + " " + uuid)
    def getTarget(bot: Bot): Option[String] = entity.get(bot).map(_.target)
    def addOrUpdate(bot: Bot, target: String, authenticated: Option[Boolean] = None) {
      if (entity.isDefinedAt(bot)) {
        log.debug("entity " + entity(bot) + " already exists")
        authenticated match {
          case Some(authState) =>
            if (entity(bot).authenticated != authState)
              entity(bot).authenticated = authState
          case None =>
        }
        entity(bot).lastActivity = System.currentTimeMillis
      } else {
        val authState = authenticated match {
          case Some(authState) => authState
          case None => false
        }
        val subscriber = subscribeBot(bot)
        val value = BotEntity(publish _)(bot, target, subscriber, new AtomicInteger(bot.priority), new AtomicBoolean(authState),
          new AtomicInteger(0), new AtomicBoolean(bot.history.exists(_ == Bot.Event.Connected)))
        log.debug("add entity bot " + value.bot.getClass.getName.split('.').last + " with target " + value.target + " for " + uuid)
        entity(bot) = value
        publish(HexapodChanged("add bot to entities ", uuid, kind, bot))
        if (entity(bot).authenticated == false)
          bot.authenticate(target)
      }
    }
    def isAllowCommand(bot: Bot, command: String): Boolean = {
      command.split(' ').head match {
        case _ => entity.get(bot).exists(_.authenticated == true)
      }
    }
    def bestEntity(): Option[BotEntity] = {
      entity.values.filter(n => n.health == true && n.authenticated == true) match {
        case Nil => None
        case x => Some(x.maxBy(_.priority))
      }
    }
    def dispose(replaceBy: Hexapod = null) {
      Hexapod.publishFromCompanion(Hexapod.Event.Disposed(box, replaceBy))
      subscribers.foreach(t => t._1.removeSubscription(t._2.asInstanceOf[Subscriber[BotEvent, t._1.Pub]]))
      removeSubscriptions()
      entity.clear()
      instance = null
    }
    private def subscribeBot(bot: Bot): Subscriber[_, _] = {
      val subscriber = new Subscriber[BotEvent, bot.Pub] {
        def notify(pub: bot.Pub, event: BotEvent): Unit = {
          event match {
            case Bot.Event.ReceiveMessage(_, _) =>
              entity(bot).health = true
              entity(bot).lastActivity = System.currentTimeMillis()
            case Bot.Event.Disconnected =>
              entity(bot).health = false
            case Bot.Event.Connecting(timestamp) =>
              entity(bot).health = false
            case Bot.Event.UserLeave(target) =>
              entity(bot).health = false
            case _ =>
          }
        }
      }
      bot.subscribe(subscriber)
      subscribers = subscribers :+ ((bot, subscriber))
      subscriber
    }
  }
  case class BotEntity(publish: HexapodChanged => Unit)(
    val bot: Bot,
    val target: String,
    val subscriber: Subscriber[_, _],
    _priority: AtomicInteger,
    _authenticated: AtomicBoolean = new AtomicBoolean(false),
    _authenticationAttempt: AtomicInteger = new AtomicInteger(0),
    _health: AtomicBoolean = new AtomicBoolean(false),
    _lastActivity: AtomicLong = new AtomicLong(System.currentTimeMillis)) {
    def priority = _priority.get()
    def priority_=(n: Int) { publish(HexapodChanged("changed priority to " + n + " ", uuid, kind, bot)); _priority.set(n) }
    def authenticated = _authenticated.get()
    def authenticated_=(n: Boolean) = if (n != _authenticated.get()) { publish(HexapodChanged("changed authenticated to " + n + " ", uuid, kind, bot)); _authenticated.set(n) }
    def authenticationAttempt = _authenticationAttempt.get()
    def authenticationAttempt_=(n: Int) = if (n != _authenticationAttempt.get()) { publish(HexapodChanged("changed authenticationAttempt to " + n + " ", uuid, kind, bot)); _authenticationAttempt.set(n) }
    def health = _health.get()
    def health_=(n: Boolean) = if (n != _health.get()) { publish(HexapodChanged("changed health to " + n + " ", uuid, kind, bot)); _health.set(n) }
    def lastActivity = _lastActivity.get()
    def lastActivity_=(n: Long) = { publish(HexapodChanged("changed lastActivity to " + n + " ", uuid, kind, bot)); _lastActivity.set(n) }
  }
}

object Hexapod {
  implicit def Object2Inner(o: Hexapod.type): ObjectH = if (initialized != null) {
    initialized
  } else {
    val message = "Hexapod singleton uninitialized"
    log.error(message)
    throw new IllegalStateException(message)
  }
  implicit def Instance2Inner(o: Hexapod): Hexapod#InstanceH = if (o.instance != null) {
    o.instance.asInstanceOf[Hexapod#InstanceH]
  } else {
    val message = "Hexapod instance " + o + " disposed"
    log.error(message)
    throw new IllegalStateException(message)
  }
  private val log = LoggerFactory.getLogger(classOf[Hexapod].getName)
  private var initialized: ObjectH = null
  def init(config: Config) = {
    val keyset = loadKeySet(config) match {
      case Some(keyset) =>
        log.info("keyset " + keyset + "loaded successful")
        keyset
      case None =>
        import scala.annotation.tailrec
        @tailrec
        def iter(buff: String, n: Int, acc: Seq[String] = Seq()): Seq[String] = {
          buff.size < n match {
            case true => acc :+ buff
            case false => iter(buff.drop(n), n, acc :+ buff.take(n))
          }
        }
        log.warn("load keyset failed")
        val keyset = generateKeySet(config)
        config.setList("keyset", iter(keyset.getEncoded64(), 80))
        config.save()
        keyset
    }
    initialized = new ObjectH(config, keyset.unlock(config.password), new File(config.getString("keysetStorage", "storage.keyset")))
    // add keyset to storage if not exists
    if (!initialized.locked.isDefinedAt(config.uuid)) {
      initialized.locked(config.uuid) = keyset
      initialized.saveKeys()
    }
  }
  private def loadKeySet(config: Config): Option[KeySet[Locked]] = {
    log.trace("check OpenPGP keyset")
    if (config.getList("keyset") == None)
      return None
    try {
      val key = config.getList("keyset").mkString("")
      if (key.isEmpty)
        None
      else
        Some(KeySet(key))
    } catch {
      case e =>
        log.warn(e.getMessage, e)
        None
    }
  }
  private def generateKeySet(config: Config): KeySet[Locked] =
    OpenPGP.generate(config.uuid + " <" + config.uuid + "@de.digimead.org>", config.password)
  /**
   * Singleton
   */
  class ObjectH(val config: Config, val unlocked: KeySet[Unlocked], val keysetStorage: File) extends Publisher[HexapodEvent]
    with SubscribeSelf[HexapodEvent] {
    val log = LoggerFactory.getLogger(classOf[Hexapod].getName)
    val uuid = config.uuid
    val hexapod = new HashMap[UUID, Hexapod] with SynchronizedMap[UUID, Hexapod]
    val locked = new HashMap[UUID, KeySet[Locked]] with SynchronizedMap[UUID, KeySet[Locked]]
    loadKeys()
    log.info("alive")
    def apply(uuid: UUID): Option[Hexapod] = hexapod.get(uuid)
    def apply(bot: Bot, target: String): Option[Hexapod] = hexapod.values.find(_.entity.values.exists(e => {
      e.bot == bot && e.target == target
    }))
    def loadKeys(file: File = keysetStorage) = synchronized {
      assert(file != null)
      log.trace("load keysets from file " + file)
      var source: BufferedSource = null
      try {
        source = scala.io.Source.fromFile(file)
        source.getLines.foreach(line => try {
          val parts = line.split(" ")
          val uuid = UUID.fromString(parts(0).trim)
          val keyset = parts(1).trim
          locked(uuid) = KeySet(keyset)
        } catch {
          case e => log.warn(e.getMessage() + " for line: " + line)
        })
      } catch {
        case e => log.warn(e.getMessage())
      } finally {
        if (source != null)
          source.close()
      }
    }
    def saveKeys(file: File = keysetStorage) = synchronized {
      assert(file != null)
      log.trace("save keysets to file " + file)
      var printWriter: PrintWriter = null
      // maybe append?
      try {
        printWriter = new PrintWriter(new FileWriter(file))
        locked.foreach(keyset => printWriter.println(keyset._1.toString + " " + keyset._2.getEncoded64()))
      } catch {
        case e => log.warn(e.getMessage())
      } finally {
        if (printWriter != null)
          printWriter.close
      }
    }
    def register(uuid: UUID, keyset: String): Boolean = synchronized {
      if (locked.isDefinedAt(uuid) || keyset.trim == "") {
        false
      } else {
        locked(uuid) = KeySet(keyset.trim)
        publish(Event.Register(uuid, locked(uuid)))
        true
      }
    }
    def notify(event: HexapodEvent) = event match {
      case Event.New(hexapod) =>
        log.debug("new " + hexapod)
      case Event.Register(uuid, password) =>
        log.debug("register " + uuid)
        saveKeys()
      case Event.Disposed(oldH, null) =>
        log.debug("dispose " + oldH.uuid)
      case Event.Disposed(oldH, newH) =>
        log.debug("dispose and replace " + oldH.uuid)
    }
    private[Hexapod] def publishFromCompanion(event: HexapodEvent) = publish(event)
  }
  object Event {
    case class New(val hexapod: Hexapod) extends HexapodEvent
    case class Register(val uuid: UUID, val keyset: KeySet[Locked]) extends HexapodEvent
    case class Disposed(val oldHexapod: Hexapod, val newHexapod: Hexapod) extends HexapodEvent
  }
}

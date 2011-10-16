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

import java.util.UUID
import java.util.concurrent.{ Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import org.digimead.documentumelasticus.helper.Config
import org.digimead.documentumelasticus.helper.Publisher
import org.digimead.documentumelasticus.hexapod.archinid.Archinid
import org.digimead.documentumelasticus.hexapod.bot.Bot
import org.digimead.documentumelasticus.hexapod.bot.BotEvent
import scala.actors.Actor
import scala.actors.Future
import scala.collection.mutable.Subscriber
import scala.collection.mutable.{ HashMap, SynchronizedMap }
import scala.collection.immutable.{ HashMap => IHashMap }

sealed trait PoolEvent

trait PoolSingleton extends Publisher[PoolEvent] {
  val entityType: IHashMap[String, Class[_ <: Bot]] // for example Map("irc" -> ....getClass, "jabber" -> ....getClass)
  protected val nodeMap = new HashMap[UUID, Archinid] with SynchronizedMap[UUID, Archinid]
  protected val initialized: AtomicBoolean = new AtomicBoolean(false)
  // type -> server -> bot
  protected lazy val entity = IHashMap(entityType.keys.map(e => {
    log.trace("add " + e + " bot type to pool with " + entityType(e))
    e -> (new HashMap[String, Bot] with SynchronizedMap[String, Bot])
  }).toSeq: _*)
  protected val watchdog = Executors.newSingleThreadScheduledExecutor()
  protected var config: Config = null
  protected def createBot(botType: String, botServer: String, config: Config): Bot
  def initialize(c: Config) {
    log.info("initialize " + getClass.getName())
    config = c
  }
  def kick() {
    log.trace("kick")
    entity.values.map(_.values).flatten.foreach(b => Bot.Message.Reconnect)
  }
  def run() {
    log.info("run")
    entityType.keys.foreach(t => {
      val server = config.getConfigMap(t + ".server").map(cm => cm.keys).getOrElse(List()).toSeq
      log.debug("run " + t + " for servers [" + server.mkString(",") + "]")
      server.foreach(s => {
        entity(t)(s) = createBot(t, s, config)
      })
    })
    entities.foreach(entity => {
      val subscriber = new Subscriber[BotEvent, entity.Pub] {
        def notify(pub: entity.Pub, event: BotEvent): Unit = {
          event match {
            case e@Bot.Event.Connected => publish(Event.Changed(entity, e))
            case e@Bot.Event.Disconnected => publish(Event.Changed(entity, e))
            case _ =>
          }
        }
      }
      entity.subscribe(subscriber)
    })
    val timeout = config.getInt("heartbeat").getOrElse(10) * 1000 // 10 seconds
    val runnable = new Runnable { def run = entities.par.foreach(b => b ! Bot.Message.Heartbeat) }
    watchdog.scheduleAtFixedRate(runnable, 0, timeout, TimeUnit.MILLISECONDS)
    entities.par.foreach(b => b ! Bot.Message.Connect)
  }
  def dispose() {
    watchdog.shutdown()
    log.info("dispose")
    entities.par.foreach(_.dispose)
    entity.values.foreach(_.clear)
  }
  def exists(bot: Bot, target: String): Boolean = nodeMap.values.exists(a => {
    a.id.exists(_ == target) && a.entity.exists(_ == bot)
  })
  def addNode(bot: Bot, target: String) {
    Archinid.status(bot, target) match {
      case Some(info) =>
        val archinid = if (nodeMap.isDefinedAt(info.uuid))
          nodeMap(info.uuid)
        else
          new Archinid(info.uuid, info.publicIP, info.consolePort, this)
        updateNode(archinid, info)
      case _ =>
        log.warn("add archinid " + target + " failed")
    }
  }
  def addNode(archinid: Archinid) = nodeMap.synchronized {
    val isEmpty = nodeMap.isEmpty
    val uuid = archinid.uuid.toString
    log.debug("add archinid with uuid " + uuid)
    if (nodeMap.isDefinedAt(archinid.uuid))
      log.trace("archinid " + uuid + " already defined")
    else
      nodeMap(archinid.uuid) = archinid
    if (isEmpty) {
      log.info("pool ready, first archinid is " + uuid)
      publish(Event.Ready)
    }
  }
  def bestNode = nodeMap.head._2
  def delNode(uuid: UUID) = nodeMap.synchronized {
    nodeMap.remove(uuid)
  }
  def updateNode(archinid: Archinid, info: Info)
  def isReady(timeout: Int = 0): Boolean = {
    val startT = System.currentTimeMillis
    val endT = startT + timeout
    while (System.currentTimeMillis < endT && nodeMap.isEmpty)
      Thread.sleep(100)
    !nodeMap.isEmpty
  }
  def entities = entity.values.map(_.values).flatten
  def entities(typeName: String) = entity.getOrElse(typeName, new HashMap[String, Bot]())
  object Event {
    case object Ready extends PoolEvent
    case class Changed(bot: Bot, e: BotEvent) extends PoolEvent
  }
}

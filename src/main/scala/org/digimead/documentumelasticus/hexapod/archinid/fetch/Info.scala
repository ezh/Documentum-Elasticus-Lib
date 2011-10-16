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

package org.digimead.documentumelasticus.hexapod.archinid.fetch
import de.javawi.jstun.util.Address

import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ Executors, TimeUnit }
import net.lag.configgy.Config
import net.sf.ehcache.{ CacheManager, Element }
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{ BasicResponseHandler, DefaultHttpClient }
import org.digimead.documentumelasticus.hexapod.Hexapod
import org.digimead.documentumelasticus.hexapod.PoolEvent
import org.digimead.documentumelasticus.hexapod.PoolSingleton
import org.digimead.documentumelasticus.hexapod.bot.irc.IRCEntity
import org.digimead.documentumelasticus.hexapod.bot.jabber.JabberEntity
import org.digimead.documentumelasticus.hexapod.{ Info => HInfo, InfoAkka, InfoIRC, InfoJabber }
import org.slf4j.LoggerFactory
import scala.actors.Actor
import scala.collection.mutable.Subscriber

private class Info(config: Config, Pool: PoolSingleton, timeout: Long) extends Actor {
  private val log = LoggerFactory.getLogger(getClass.getName)
  private var ip = new AtomicReference[String](null)
  private lazy val manager = {
    if (config.getString("cache") == None) {
      val manager = new CacheManager()
      manager.addCache("SimpleCache")
      manager
    } else {
      try {
        new CacheManager(config.getString("cache").get)
      } catch {
        case e =>
          log.error(e.getMessage, e)
          val manager = new CacheManager()
          manager.addCache("SimpleCache")
          manager
      }
    }
  }
  private lazy val cacheNames = manager.getCacheNames()
  private lazy val cache = manager.getCache(cacheNames.head)
  start()
  log.trace("alive")
  def getIP(): String = {
    var result = ip.get()
    while (result == null) {
      Thread.sleep(100)
      result = ip.get()
    }
    result
  }
  def act() {
    loop {
      react {
        case Fetch => fetch()
      }
    }
  }
  private def fetch() = synchronized {
    log.trace("fetch info")
    ip.set(fetchIP())
    Info.info.set(fetchSelf())
  }
  private def fetchIP(): String = {
    log.trace("fetch ip")
    val anchor = "http://www.myip.ru/get_ip.php"
    val element = cache.get(anchor)
    if (element != null) {
      log.trace("get cached " + anchor)
      element.getValue.asInstanceOf[String]
    } else {
      log.trace("get " + anchor)
      val client = new DefaultHttpClient()
      val response: String = try {
        val method = new HttpGet(anchor)
        val handler: ResponseHandler[String] = new BasicResponseHandler()
        val content = client.execute[String](method, handler)
        val ipRegExp = """<TR><TD bgcolor=white align=center valign=middle>(\d+\.\d+\.\d+\.\d+)</TD></TR>""".r
        if (content.indexOf("""<TR><TD bgcolor=white align=center valign=middle>""") > 0)
          ipRegExp.findFirstMatchIn(content).map(_.group(1)).getOrElse("")
        else {
          log.error("broken content: " + content)
          ""
        }
      } catch {
        case e =>
          log.error(e.getMessage, e)
          ""
      } finally {
        client.getConnectionManager.shutdown
      }
      // result
      if (response == "") {
        log.error("fetch ip failed")
        null
      } else {
        cache.put(new Element(anchor, response))
        response
      }
    }
  }
  private def fetchSelf(): HInfo = {
    val irc: Seq[InfoIRC] = Pool.entities("irc").map(bot => {
      val pircbot = bot._2.asInstanceOf[IRCEntity].pircbot
      InfoIRC(pircbot.nick, pircbot.uri, pircbot.getChannels.toSeq)
    }).toSeq
    val jabber: Seq[InfoJabber] = Pool.entities("jabber").map(bot => {
      val smack = bot._2.asInstanceOf[JabberEntity].smack
      InfoJabber(smack.nick, smack.options.getHost)
    }).toSeq
    val akka: Seq[InfoAkka] = Seq()
    HInfo(
      UUID.fromString(config.getString("uuid").get),
      new Address(getIP()),
      Hexapod.config.getInt("consolePort", -1),
      irc,
      jabber,
      akka)
  }
  case object Fetch
}

object Info {
  private val log = LoggerFactory.getLogger(getClass.getName)
  private val info = new AtomicReference[HInfo](null)
  private var instance: Info = null
  private var scheduler: ScheduledExecutorService = null
  private var unsubscribe: () => Unit = null
  private val defaultTimeout = 60 * 60 * 1000
  def run(config: Config, Pool: PoolSingleton, timeout: Long = defaultTimeout) = synchronized {
    log.trace("start service")
    assert(instance == null && scheduler == null && unsubscribe == null)
    instance = new Info(config, Pool, timeout)
    scheduler = Executors.newSingleThreadScheduledExecutor()
    val runnable = new Runnable { def run = instance ! instance.Fetch }
    scheduler.scheduleAtFixedRate(runnable, 0, timeout, TimeUnit.MILLISECONDS)
    val subscriber = new Subscriber[PoolEvent, Pool.Pub] {
      def notify(pub: Pool.Pub, event: PoolEvent): Unit = {
        event match {
          case Pool.Event.Changed(bot, event) => instance ! instance.Fetch
          case _ =>
        }
      }
    }
    Pool.subscribe(subscriber.asInstanceOf[Pool.Sub])
    unsubscribe = () => { Pool.removeSubscription(subscriber) }
  }
  def dispose() = synchronized {
    log.trace("stop service")
    assert(instance != null && scheduler != null && unsubscribe != null)
    scheduler.shutdownNow()
    unsubscribe()
    instance = null
    scheduler = null
    unsubscribe = null
  }
  def get(): HInfo = {
    var result = info.get()
    while (result == null) {
      Thread.sleep(100)
      result = info.get()
    }
    result
  }
}

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

package org.digimead.documentumelasticus.archinid.fetch

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import net.sf.ehcache.CacheManager
import net.sf.ehcache.Element
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.DefaultHttpClient
import org.digimead.documentumelasticus.archinid.Component
import org.digimead.documentumelasticus.archinid.Component
import org.digimead.documentumelasticus.helper.Config
import org.slf4j.LoggerFactory
import scala.actors.Actor
import scala.collection.mutable.Subscriber
import scala.util.parsing.json._
import java.net.URLEncoder._

private class Components(val config: Config, timeout: Long) extends Actor {
  private val log = LoggerFactory.getLogger(getClass.getName)
  private val BASE_URI = "http://github.com/api/v2/json";
  start()
  log.trace("alive")
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
  def act() {
    loop {
      react {
        case Fetch => fetch()
      }
    }
  }
  private def fetch() = synchronized {
    log.trace("fetch components")
    // get components
    val anchor = BASE_URI + "/repos/show/ezh"
    val element = cache.get(anchor)
    if (element != null) {
      log.trace("get cached " + anchor)
      Components.components.set(element.getValue.asInstanceOf[Seq[Component]])
    } else {
      log.trace("get " + anchor)
      val client = new DefaultHttpClient()
      val handler: ResponseHandler[String] = new BasicResponseHandler()
      try {
        val method = new HttpGet(anchor)
        val components: Seq[Component] = JSON.parseFull(client.execute[String](method, handler)) match {
          case Some(json) =>
            json.asInstanceOf[Map[String, List[Map[String, Any]]]]("repositories").filter(r => r("name").
              asInstanceOf[String].startsWith("Documentum-Elasticus-")).map(r => {
              val anchor = BASE_URI + "/repos/show/ezh/" + r("name").asInstanceOf[String] + "/tags"
              val method = new HttpGet(anchor)
              val details = JSON.parseFull(client.execute[String](method, handler)).map(json => json.asInstanceOf[Map[String, Map[String, Any]]]("tags")).getOrElse(Map[String, Any]())
              new Component(
                r("url").asInstanceOf[String],
                r("homepage").asInstanceOf[String],
                r("watchers").asInstanceOf[Double].toInt,
                r("created_at").asInstanceOf[String],
                r("pushed_at").asInstanceOf[String],
                r("forks").asInstanceOf[Double].toInt,
                r("name").asInstanceOf[String],
                r("description").asInstanceOf[String],
                r("open_issues").asInstanceOf[Double].toInt,
                details.map(tag => {
                  val name = tag._1.asInstanceOf[String]
                  val hash = tag._2.asInstanceOf[String]
                  val base = "https://github.com/ezh/" + r("name").asInstanceOf[String] + "/tree/" + encode(name, "US-ASCII") + "/dist/"
                  val anchor = BASE_URI + "/blob/all/ezh/" + r("name").asInstanceOf[String] + "/" + encode(name, "US-ASCII")
                  val method = new HttpGet(anchor)
                  val blobs = JSON.parseFull(client.execute[String](method, handler)).map(json => json.asInstanceOf[Map[String, Map[String, Any]]]("blobs")).getOrElse(Map[String, Any]())
                  val files = blobs.filter(blob => blob._1.startsWith("dist/")).map(blob => {
                    blob._1.substring(5) -> ("""http://github.com/ezh/""" + encode(r("name").asInstanceOf[String], "US-ASCII") +
                      "/blob/" + encode(tag._1, "US-ASCII") +
                      "/" + encode(blob._1, "US-ASCII") + "?raw=true")
                  })
                  name.asInstanceOf[String] -> Component.Version(hash, base, files)
                }))
            })
          case None => Seq()
        }
        cache.put(new Element(anchor, components))
        Components.components.set(components)
      } catch {
        case e =>
          log.error(e.getMessage(), e)
          Seq()
      } finally {
        client.getConnectionManager.shutdown
      }
    }
  }
  case object Fetch
}

object Components {
  private val log = LoggerFactory.getLogger(getClass.getName)
  private var components = new AtomicReference[Seq[Component]](Seq())
  private var instance: Components = null
  private var scheduler: ScheduledExecutorService = null
  private val defaultTimeout = 60 * 60 * 1000
  def run(config: Config, timeout: Long = defaultTimeout) = synchronized {
    import org.digimead.documentumelasticus.archinid.pool
    log.trace("start service")
    assert(instance == null && scheduler == null)
    instance = new Components(config, timeout)
    scheduler = Executors.newSingleThreadScheduledExecutor()
    val runnable = new Runnable { def run = instance ! instance.Fetch }
    scheduler.scheduleAtFixedRate(runnable, 0, timeout, TimeUnit.MILLISECONDS)
  }
  def dispose() = synchronized {
    log.trace("stop service")
    assert(instance != null && scheduler != null)
    scheduler.shutdownNow()
    instance = null
    scheduler = null
  }
  def get(group: String): Seq[Component] = {
    var result = components.get()
    while (result == null) {
      Thread.sleep(100)
      result = components.get()
    }
    group.toUpperCase match {
      case "ALL" => result
      case "STANDARD" =>
        val filter = instance.config.getList("components.standardFilter")
        result.filter(c => filter.contains(c.name))
      case _ =>
        log.warn("unknown components group " + group)
        Seq()
    }
  }
}

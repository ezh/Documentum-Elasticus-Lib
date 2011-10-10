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

package org.digimead.documentumelasticus.archinid

import java.net.InetAddress
import java.util.Date
import org.digimead.documentumelasticus.Hexapod
import org.digimead.documentumelasticus.archinid.fetch.{ Info => FInfo }
import org.digimead.documentumelasticus.helper.Publisher
import scala.actors.Actor
import scala.util.matching.Regex
import sun.management.ManagementFactory

package process {
  trait Message
  trait Event
  case class Command(
    val command: String,
    val args: Seq[String],
    val sender: Option[Hexapod],
    val hash: Int,
    val source: Source)
  case class Source(
    val bot: Bot,
    val target: String)
}

trait ProcessSingleton extends Publisher[process.Event] with Actor {
  type CommadsDispatcher = PartialFunction[process.Command, Option[String]]
  val commandsDispatcher: CommadsDispatcher
  start()
  def act {
    loop {
      react {
        case cmd: Message.Command =>
          try { command(commandsDispatcher, cmd) } catch { case e => log.error(e.getMessage, e) }
        case message =>
          log.error("unknown message " + message)
      }
    }
  }
  def checkCommand(command: Message.Command): Boolean = {
    true
  }
  def command(f: PartialFunction[process.Command, Option[String]], cmd: Message.Command): Unit = {
    if (checkCommand(cmd)) {
      val args = cmd.buffer.split(" ").map(_.trim)
      cmd.bot.log.info("process command " + args.head.toLowerCase() + " from " + cmd.target)
      val source = process.Source(cmd.bot, cmd.target)
      implicit val hash = cmd.buffer.trim.hashCode()
      val command = process.Command(args.head.toLowerCase(), args.tail, cmd.sender, hash, source)
      if (f.isDefinedAt(command)) {
        publish(Event.Process(cmd))
        val fResult = try {
          f(command)
        } catch {
          case e =>
            log.error(e.getMessage(), e)
            None
        }
        fResult match {
          case Some("") =>
            cmd.bot.send(cmd.target, empty.get)
          case Some(reply) =>
            cmd.bot.send(cmd.target, reply)
          case None =>
            log.debug("no reply for " + command)
        }
      } else {
        log.debug("skip " + cmd)
      }
    } else {
      log.warn("broken " + cmd)
    }
  }
  def empty()(implicit hash: Int): Option[String] = {
    log.trace("empty " + hash)
    Some("--- [" + hash + "#0/0] message block ---\n")
  }
  def block(content: String, pos: Int = 1, max: Int = 1)(implicit hash: Int): Option[String] = {
    log.trace("block " + hash)
    assert(pos > 0)
    assert(pos <= max)
    Some("--- [" + hash + "#" + pos + "/" + max + "] begin of message block ---\n" +
      content + "\n--- end of message block ---\n")
  }
  def ping()(implicit hash: Int): Option[String] = {
    log.trace("ping")
    Some("--- [" + hash + "#1/1] " + Hexapod.uuid + " ---\n")
  }
  def status(): String = {
    log.trace("get status")
    var response: String = ""
    val mx = ManagementFactory.getRuntimeMXBean()
    val startTime = new Date(mx.getStartTime())
    val uptime = mx.getUptime()
    val info = FInfo.get()
    response = info.uuid + " at " + info.ip + " host " + InetAddress.getLocalHost().getHostName() + "\n"
    response += "start at " + startTime + "\n"
    response += "uptime " + (uptime / 100000).floor + " min\n"
    info.irc.foreach(irc => {
      response += "IRC: nick \"" + irc.nick + "\" server \"" + irc.server +
        "\" channels \"" + irc.channels.mkString(", ") + "\"\n"
    })
    info.jabber.foreach(jabber => {
      response += "Jabber: nick \"" + jabber.nick + "\" server \"" + jabber.server + "\"\n"
    })
    // TODO Akka
    response
  }
  object Message {
    case class Command(bot: Bot, target: String, sender: Option[Hexapod], buffer: String) extends process.Message
  }
  object Event {
    case class Process(command: Message.Command) extends process.Event
  }
}

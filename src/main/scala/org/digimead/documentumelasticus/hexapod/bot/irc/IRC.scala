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

package org.digimead.documentumelasticus.hexapod.bot.irc

import java.util.UUID
import java.util.concurrent.atomic.{ AtomicInteger, AtomicLong, AtomicReference }
import java.util.zip.CRC32
import org.digimead.documentumelasticus.helper.SubscribeSelf
import org.digimead.documentumelasticus.hexapod.Hexapod
import org.digimead.documentumelasticus.hexapod.ProcessSingleton
import org.digimead.documentumelasticus.hexapod.Reply
import org.digimead.documentumelasticus.hexapod.Request
import org.digimead.documentumelasticus.hexapod.bot.Bot
import org.digimead.documentumelasticus.hexapod.bot.BotEvent
import org.jibble.pircbot.{ PircBot, User }
import org.slf4j.{ Logger, LoggerFactory }
import scala.actors.{ Actor, OutputChannel }
import scala.actors.Futures._
import scala.collection.mutable.{ HashMap, HashSet, Publisher, Subscriber, SynchronizedMap, SynchronizedSet }

sealed trait IRCEvent
sealed trait IRCMessage
sealed trait IRCMessageType

trait IRCEntity extends Bot {
  private val that = this
  val uuid = UUID.fromString(config.getString("uuid").getOrElse(""))
  val IRC: IRCSingleton
  val Process: ProcessSingleton
  lazy val pircbot: Bot = new Bot(log)
  val priority = 100
  val timeout = 60000 // 60s timeout for any reaction; very slow transport
  val lock = new java.util.concurrent.locks.ReentrantLock
  var incompleteBlockBuffer = new HashMap[IncompleteBlockKey, Seq[IncompleteBlockValue]] with SynchronizedMap[IncompleteBlockKey, Seq[IncompleteBlockValue]]
  val skipUserMessage = new HashSet[String] with SynchronizedSet[String]
  config.getList("irc.server." + server + ".skip_user").foreach(skipUserMessage(_) = true)
  start()
  def act() {
    loop {
      react {
        case Bot.Message.Connect =>
          try { connect() } catch { case e => log.error(e.getMessage, e) }
        case Bot.Message.Disconnect =>
          try { disconnect() } catch { case e => log.error(e.getMessage, e) }
        case Bot.Message.Reconnect =>
          try { reconnect() } catch { case e => log.error(e.getMessage, e) }
        case Bot.Message.Heartbeat =>
          try { heartbeat() } catch { case e => log.error(e.getMessage, e) }
        case IRC.Message.RegisterPassword(password) =>
          log.trace("set password for account")
          send("NickServ", "register " + password + " " + uuid.toString + "@digimead.org")
          val s = sender
          future { s ! getMessage("NickServ", timeout, timeout / 5, config.getList("irc.server." + server + ".nickserv_msg")) }
        case request: Request =>
          try {
            command(request.target, request.t.message, request.force.get)
          } catch { case e => log.error(e.getMessage, e) }
        case message: IRC.Message.Receive =>
          try {
            // add queue buffer between sender and processor
            that.publish(Bot.Event.ReceiveMessage(message.sender.trim, message.message.trim))
            processMessage(message.sender.trim, message.login.trim, message.hostname.trim, message.message.trim)
          } catch { case e => log.error(e.getMessage, e) }
        case message =>
          log.error("unknow message" + message)
      }
    }
  }
  override def notify(event: BotEvent) = {
    super.notify(event)
    event match {
      case Bot.Event.Other(v @ value) if v.isInstanceOf[IRCEvent] =>
        v.asInstanceOf[IRCEvent] match {
          case that.IRC.Event.Join(target, channel) =>
            if (target == pircbot.nick && !history.exists(_ == Bot.Event.Connected) &&
              pircbot.joinCounter.get == config.getList("irc.server." + server + ".channels").size)
              publish(Bot.Event.Connected)
            if (target != pircbot.nick)
              publish(Bot.Event.UserEnter(target))
          case that.IRC.Event.Quit(target, channel) =>
            if (target != pircbot.nick)
              publish(Bot.Event.UserLeave(target))
          case that.IRC.Event.UserList(channel, users) =>
            log.debug("channel users: " + users.map(_.getNick).mkString(","))
            users.filter(u => u.getNick != pircbot.nick).foreach(u => publish(Bot.Event.UserEnter(u.getNick())))
          case that.IRC.Event.Connected =>
        }
      case Bot.Event.UserEnter(target) =>
        if (target.startsWith("_DA_"))
          publish(Bot.Event.UserArchinid(target))
      case _ =>
    }
  }
  protected def connect() = connect(false)
  protected def connect(silent: Boolean): Unit = lock.synchronized {
    if (!silent)
      log.trace("connect")
    clearEvent()
    publish(Bot.Event.Connecting(System.currentTimeMillis))
    pircbot.prepare(nick(), uri())
    val serverChannels = config.getList("irc.server." + server + ".channels")
    if (serverChannels.isEmpty) {
      log.warn("key archinid.irc.server." + server + ".channels not found in configuration file")
      publish(Bot.Event.Disconnected)
      return
    }
    if (pircbot.uri == "") {
      log.warn("key archinid.irc.server." + server + ".uri not found in configuration file")
      publish(Bot.Event.Disconnected)
      return
    }
    if (pircbot.nick == "") {
      log.warn("uuid not found in configuration file")
      publish(Bot.Event.Disconnected)
      return
    }
    try {
      log.info("try to connect to network [" + server + "] at [" + pircbot.uri + "] as " + pircbot.nick)
      future {
        try {
          pircbot.connect(pircbot.uri)
        } catch {
          case e => log.error(e.getMessage, e)
        }
      }
      waitEvent(Seq(Bot.Event.Other(IRC.Event.Connected), Bot.Event.Disconnected), timeout, true)
      if (pircbot.isConnected) {
        future { serverChannels.par.foreach(pircbot.joinChannel(_)) }
        waitEvent(Bot.Event.Connected, timeout, true)
        if (!history.exists(_ == Bot.Event.Connected)) {
          log.warn("connection failed")
          disconnect()
        } else {
          register()
        }
      } else {
        disconnect()
        log.warn("connection failed")
      }
    } catch {
      case e =>
        log.warn(e.getMessage, e)
        disconnect()
    }
  }
  protected def reconnect() = {
    log.trace("reconnect")
    disconnect(true)
    connect(true)
  }
  protected def disconnect() = disconnect(false)
  protected def disconnect(silent: Boolean): Unit = lock.synchronized {
    if (!silent)
      log.trace("disconnect")
    if (pircbot.isConnected) {
      future { pircbot.disconnect() }
      waitEvent(Bot.Event.Disconnected, timeout, true)
    }
    if (!history.exists(_ == Bot.Event.Disconnected))
      publish(Bot.Event.Disconnected)
  }
  def dispose() {
    log.trace("disconnect")
    disconnect()
  }
  private def heartbeat(): Unit = lock.synchronized {
    if (lock.tryLock()) {
      try {
        val serverChannels = config.getList("irc.server." + server + ".channels")
        if (pircbot.nick != "" && pircbot.uri != "" &&
          (!pircbot.isConnected() || serverChannels.size != pircbot.getChannels().size)) {
          val lastConnecting = history.find(_.isInstanceOf[Bot.Event.Connecting]).getOrElse(Bot.Event.Connecting(0)).asInstanceOf[Bot.Event.Connecting]
          if (System.currentTimeMillis - lastConnecting.timestamp > 60000)
            if (reconnectFlag.get)
              reconnect()
        }
      } catch {
        case e => log.error(e.getMessage, e)
      } finally {
        lock.unlock()
      }
    }
  }
  def ping(target: String) = ping(target, 3)
  def ping(target: String, retry: Int) = Request(target, this, Request.Message.Ping).setRetry(retry).setLogger(log).setTimeout(timeout).send[Option[Any]] != None
  protected def sendMessage(target: String, message: String): Unit = {
    val limit = 400
    message.split("\n").foreach(msg => {
      if (msg.length < limit) {
        pircbot.sendMessage(target, msg)
      } else {
        import scala.annotation.tailrec
        @tailrec
        def iter(buff: String, n: Int, acc: Seq[String] = Seq()): Seq[String] = {
          buff.size < n match {
            case true => acc :+ buff
            case false => iter(buff.drop(n), n, acc :+ buff.take(n))
          }
        }
        val checksum = new CRC32()
        checksum.update(msg.getBytes)
        val summ = checksum.getValue
        val seq = iter(msg, limit)
        for (i <- (0 until seq.size).reverse) {
          pircbot.sendMessage(target, "[[[" + i + " " + summ + "]]] " + seq(i))
        }
      }
    })
  }
  private def register() {
    val password = config.getString("irc.server." + server + ".password").getOrElse("")
    if (password.nonEmpty) {
      log.info("register with NickServ")
      future { pircbot.identify(password) }
    } else {
      log.warn("password not found")
    }
  }
  private def processMessage(sender: String, login: String, hostname: String, message: String) {
    try {
      if (skipIncommingMessage.get()) {
        skipIncommingMessage.set(false)
        return
      }
      if (skipUserMessage(sender))
        return
      if (login == "")
        return
      if (hostname == "")
        return
      val key = IncompleteBlockKey(sender, login, hostname)
      message.trim match {
        case message if (message.startsWith("--- [") && message.endsWith("] message block ---")) =>
          // empty message block
          val id = message.substring(5, message.length - 19).split("#")
          val hash = (id(0)).toInt + sender.hashCode
          val (blockN, blocksTotal) = id(1).split("/").map(_.toInt).toSeq match { case x => (x.head, x.last) }
          log.debug("[BLOCK] empty block " + blockN + "/" + blocksTotal + " with hash " + hash)
          Request ! Reply(this.hashCode, hash, blockN, blocksTotal, "")
        case message if (message.matches("""--- \[[-0-9]+#\d+/\d+\] [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12} ---""")) =>
          // detect pong message
          val id = message.drop(5).dropRight(42).split("#")
          val hash = (id(0)).toInt + sender.hashCode
          val (blockN, blocksTotal) = id(1).split("/").map(_.toInt).toSeq match { case x => (x.head, x.last) }
          log.info("[BLOCK] receive pong " + blockN + "/" + blocksTotal + " with hash " + hash)
          Hexapod(this, sender).foreach(_.entity(this).health = true)
          Request ! Reply(this.hashCode, hash, blockN, blocksTotal, "pong")
        case message if (message.startsWith("--- [") && message.endsWith("] begin of message block ---")) =>
          // detect message block begin
          val id = message.substring(5, message.length - 28).split("#")
          val hash = (id(0)).toInt + sender.hashCode
          val (blockN, blocksTotal) = id(1).split("/").map(_.toInt).toSeq match { case x => (x.head, x.last) }
          log.debug("[BLOCK] begin block " + blockN + "/" + blocksTotal + " with hash " + hash)
          val value = IncompleteBlockValue(hash, blockN, blocksTotal, "", System.currentTimeMillis())
          incompleteBlockBuffer(key) = Seq(value)
        case message if (incompleteBlockBuffer.isDefinedAt(key) && message == "--- end of message block ---") =>
          // detect message block end
          val blocks = incompleteBlockBuffer(key)
          val blockHash = blocks.head.hash
          val blockN = blocks.head.count
          val blocksTotal = blocks.head.total
          log.info("[BLOCK] receive block " + blockN + "/" + blocksTotal + " with hash " + blockHash)
          val blockText = blocks.drop(1).map(_.chunk).mkString("\n").trim
          incompleteBlockBuffer.remove(key)
          Request ! Reply(this.hashCode, blockHash, blockN, blocksTotal, blockText)
        case message if (message.matches("""\[\[\[\d+ [-0-9]+\]\]\] .*""")) =>
          val n = message.drop(3).takeWhile(_ != ']').split(' ')
          val num = n(0).toInt
          val summ = n(1)
          val string = message.dropWhile(_ != ']').drop(4)
          if (!IRCEntry.longStrings.isDefinedAt(summ))
            IRCEntry.longStrings(summ) = (new AtomicLong(0), new Array[String](num + 1))
          IRCEntry.longStrings(summ)._1.set(System.currentTimeMillis())
          IRCEntry.longStrings(summ)._2(num) = string
          if (num == 0)
            IRCEntry.longStrings.remove(summ).foreach(t => {
              val longMessage = t._2.mkString
              val checksum = new CRC32()
              checksum.update(longMessage.getBytes)
              val summNew = checksum.getValue
              if (summ == summNew.toString) {
                log.debug("receive long message with checksumm " + summ)
                processMessage(sender, login, hostname, longMessage)
              } else {
                log.warn("receive broken long message with checksumm " + summ + "!=" + summNew)
              }
            })
        case message if (incompleteBlockBuffer.isDefinedAt(key)) =>
          // detect message block content
          val firstBlock = incompleteBlockBuffer(key).head
          val value = IncompleteBlockValue(firstBlock.hash, firstBlock.count, firstBlock.total, message, System.currentTimeMillis())
          incompleteBlockBuffer(key) = incompleteBlockBuffer(key) :+ value
        case message =>
          Process ! Process.Message.Command(this, sender, Hexapod(this, sender), message)
      }
    } catch {
      case e => log.warn(e.getMessage(), e)
    }
  }
  private def getMessage(target: String, timeoutBefore: Int = timeout, timeoutAfter: Int = timeout / 5, template: Seq[String] = Seq()): Option[String] = {
    var result: Option[String] = None
    def getMessageChunk(target: String, timeout: Int): Option[String] = {
      val message: AtomicReference[String] = new AtomicReference(null)
      val receiver = new Subscriber[BotEvent, Pub] {
        def notify(pub: Pub, event: BotEvent): Unit = {
          event match {
            case event: Bot.Event.ReceiveMessage =>
              if (event.target == target) {
                message.synchronized {
                  skipIncommingMessage.set(true)
                  message.set(event.message)
                  message.notifyAll
                }
              }
            case _ => ()
          }
        }
      }
      subscribe(receiver)
      message.synchronized {
        if (message.get == null)
          message.wait(timeout)
      }
      removeSubscription(receiver)
      if (message.get == null)
        None
      else
        Some(message.get)
    }
    log.trace("get message from " + target)
    var chunk: Option[String] = None
    var timeout = timeoutBefore
    do {
      chunk = getMessageChunk(target, timeout)
      result = Some(result.getOrElse("") + chunk.getOrElse(""))
      timeout = timeoutAfter
    } while (chunk != None && !template.exists(chunk.get.trim.matches(_)))
    log.trace("got message from " + target)
    result
  }
  private def nick() = IRC.getNickByUUID(uuid, role)
  private def uri() = config.getString("irc.server." + server + ".uri").getOrElse("")
  private def role() = config.getString("role").getOrElse("client")
  protected case class IncompleteBlockKey(val sender: String, val login: String, val hostname: String)
  protected case class IncompleteBlockValue(val hash: Int, val count: Int, val total: Int, val chunk: String, val timestamp: Long)
  class Bot(val log: Logger) extends PircBot {
    assert(log != null)
    private object Stash {
      var nick: String = ""
      var uri: String = ""
    }
    val joinCounter = new AtomicInteger(0)
    def nick = Stash.nick
    def uri = Stash.uri
    def prepare(nick: String, uri: String) {
      if (!Stash.nick.isEmpty)
        skipUserMessage(Stash.nick) = false
      skipUserMessage(nick) = true
      Stash.nick = nick
      Stash.uri = uri
      setName(nick)
    }
    protected override def onConnect() = {
      joinCounter.set(0)
      that.publish(Bot.Event.Other(that.IRC.Event.Connected))
    }
    protected override def onDisconnect() = that.publish(Bot.Event.Disconnected)
    protected override def onJoin(channel: String, sender: String, login: String, hostname: String) = {
      if (sender == nick)
        joinCounter.incrementAndGet()
      that.publish(Bot.Event.Other(that.IRC.Event.Join(sender, channel)))
    }
    protected override def onQuit(sender: String, login: String, hostname: String, reason: String) =
      that.publish(Bot.Event.Other(that.IRC.Event.Quit(sender, reason)))
    protected override def onUserList(channel: String, users: Array[User]) =
      that.publish(Bot.Event.Other(that.IRC.Event.UserList(channel, users)))
    protected override def onAction(sender: String, login: String, hostname: String, target: String, action: String) =
      log.trace("[ACTION] from " + target + " " + action)
    protected override def onMessage(channel: String, sender: String, login: String, hostname: String, message: String) =
      that ! IRC.Message.Receive(sender, login, hostname, message, that.IRC.Type.Message)
    protected override def onPrivateMessage(sender: String, login: String, hostname: String, message: String) =
      that ! IRC.Message.Receive(sender, login, hostname, message, that.IRC.Type.Private)
    protected override def onNotice(sender: String, login: String, hostname: String, target: String, message: String) =
      that ! IRC.Message.Receive(sender, login, hostname, message, that.IRC.Type.Notice)
  }
}

trait IRCSingleton {
  val log = LoggerFactory.getLogger(getClass.getName)
  object Message {
    case class RegisterPassword(val newPassword: String) extends IRCMessage
    case class Receive(val sender: String, val login: String, val hostname: String, val message: String, val messageType: IRCMessageType) extends IRCMessage
  }
  object Event {
    case object Connected extends IRCEvent
    case class Join(val target: String, val channel: String) extends IRCEvent
    case class Quit(val target: String, val reason: String) extends IRCEvent
    case class UserList(val channel: String, users: Array[User]) extends IRCEvent
  }
  object Type {
    case object Message extends IRCMessageType
    case object Private extends IRCMessageType
    case object Notice extends IRCMessageType
  }
  def getNickByUUID(arg: String, role: String): String = getNickByUUID(UUID.fromString(arg), role)
  def getNickByUUID(arg: UUID, role: String): String = {
    val prefix = role match {
      case "client" => "_DC_"
      case "archinid" => "_DA_"
      case role =>
        log.warn("unknown role " + role)
        "_DC_"
    }
    prefix + toIRCBase64(arg.getMostSignificantBits)
  }
  private def toIRCBase64(arg: Long): String = {
    val map1 = (('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9')).toArray[Char] ++ Array('_', '-')
    def convert(in: Array[Byte], iLen: Int): Array[Char] = {
      val oDataLen = (iLen * 4 + 2) / 3 // output length without padding
      val oLen = ((iLen + 2) / 3) * 4 // output length including padding
      val out = new Array[Char](oLen)
      var ip = 0
      var op = 0
      while (ip < iLen) {
        val i0 = in(ip) & 0xff
        ip += 1
        val i1 = if (ip < iLen) in(ip) & 0xff else 0
        ip += 1
        val i2 = if (ip < iLen) in(ip) & 0xff else 0
        ip += 1
        val o0 = i0 >>> 2
        val o1 = ((i0 & 3) << 4) | (i1 >>> 4)
        val o2 = ((i1 & 0xf) << 2) | (i2 >>> 6)
        val o3 = i2 & 0x3F
        out(op) = map1(o0)
        op += 1
        out(op) = map1(o1)
        op += 1
        out(op) = if (op < oDataLen) map1(o2) else '^'
        op += 1
        out(op) = if (op < oDataLen) map1(o3) else '^'
        op += 1
      }
      out
    }
    new String(convert(Array[Byte]((arg >> 56).toByte,
      (arg >> 48).toByte,
      (arg >> 40).toByte,
      (arg >> 32).toByte,
      (arg >> 24).toByte,
      (arg >> 16).toByte,
      (arg >> 8).toByte,
      arg.toByte), 8))
  }
}

object IRCEntry {
  val longStrings = new HashMap[String, Tuple2[AtomicLong, Array[String]]] with SynchronizedMap[String, Tuple2[AtomicLong, Array[String]]]
}

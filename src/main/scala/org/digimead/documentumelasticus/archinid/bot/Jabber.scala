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

package org.digimead.documentumelasticus.archinid.bot

import java.util.UUID
import java.util.concurrent.atomic.{ AtomicInteger, AtomicLong }
import net.lag.configgy.Config
import org.digimead.documentumelasticus.Archinid
import org.digimead.documentumelasticus.Hexapod
import org.digimead.documentumelasticus.archinid.Reply
import org.digimead.documentumelasticus.archinid.{ Bot, PoolSingleton, ProcessSingleton, Request }
import org.digimead.documentumelasticus.helper.{ Publisher, SubscribeSelf }
import org.jivesoftware.smack.Connection
import org.jivesoftware.smack.PacketListener
import org.jivesoftware.smack.filter.PacketFilter
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.{ Chat, ChatManagerListener, ConnectionConfiguration, ConnectionListener, MessageListener, Roster, RosterListener, XMPPConnection }
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.{ Message => JMessage, Packet, Presence }
import org.jivesoftware.smack.util.StringUtils
import org.slf4j.Logger
import scala.actors.Actor
import scala.actors.Futures._
import scala.collection.JavaConversions._
import scala.collection.mutable.{ HashMap, Subscriber, SynchronizedMap }

package jabber {
  sealed trait Event
  sealed trait Message
}

trait JabberEntity extends Bot with SubscribeSelf[Event] {
  private val that = this
  val uuid = UUID.fromString(config.getString("uuid").getOrElse(""))
  val server: String
  val config: Config
  val Process: ProcessSingleton
  val Jabber: JabberSingleton
  val Pool: PoolSingleton
  val priority = 1000
  lazy val smack = new Bot(log)
  val timeout = 10000 // 10s timeout for any reaction, slow transport
  val lock = new java.util.concurrent.locks.ReentrantLock
  Hexapod.subscribe(new subscriberHexapodNew())
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
        case request: Request =>
          try { command(request.target, request.t.message, request.force.get) } catch { case e => log.error(e.getMessage, e) }
        case message: that.Jabber.Event.Message =>
          try {
            // add queue buffer between sender and processor
            val sender = StringUtils.parseBareAddress(message.message.getFrom())
            that.publish(Bot.Event.ReceiveMessage(sender, message.message.getBody().trim))
            processMessage(message.chat, message.message)
          } catch { case e => log.error(e.getMessage, e) }
        case message =>
          log.error("unknow message" + message)
      }
    }
  }
  override def notify(event: Event) = {
    super.notify(event)
    event match {
      case Bot.Event.Other(v @ value) if v.isInstanceOf[jabber.Event] =>
        v.asInstanceOf[jabber.Event] match {
          case that.Jabber.Event.RosterEntriesAdded(entries) =>
            entries.foreach(e => publish(Bot.Event.UserEnter(e.asInstanceOf[String])))
          case that.Jabber.Event.RosterEntriesDeleted(entries) =>
            entries.foreach(e => publish(Bot.Event.UserLeave(e.asInstanceOf[String])))
          case that.Jabber.Event.RosterEntriesUpdated(entries) =>
            log.error("??? update entries" + entries)
          case that.Jabber.Event.RosterPresenceChanged(presence) =>
            val user = StringUtils.parseBareAddress(presence.getFrom)
            if (user != smack.nick + "@" + smack.connection.getServiceName) {
              if (presence.getType() == Presence.Type.available) {
                publish(Bot.Event.UserEnter(user))
              } else {
                publish(Bot.Event.UserLeave(user))
              }
            }
            log.debug("roster update: " + user + " " + presence)
          case that.Jabber.Event.BulkMsg(_, _) =>
          case that.Jabber.Event.NewChat(_) =>
          case that.Jabber.Event.NewRoster(_) =>
          case that.Jabber.Event.RecvMsg(_, _) =>
        }
      case Bot.Event.UserEnter(target) =>
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
    if (password() == "") {
      log.warn("key archinid.irc.server." + server + ".password not found in configuration file")
      publish(Bot.Event.Disconnected)
      return
    }
    if (nick() == "") {
      log.warn("uuid not found in configuration file")
      publish(Bot.Event.Disconnected)
      return
    }
    try {
      log.info("try to connect to network [" + server + "] at [" + uri + "] as " + smack.nick)
      if (smack.connect(nick(), password())) {
        if (!history(Bot.Event.Connected))
          publish(Bot.Event.Connected)
      } else {
        if (!history(Bot.Event.Disconnected))
          publish(Bot.Event.Disconnected)
      }
    } catch {
      case e =>
        log.warn(e.getMessage, e)
        publish(Bot.Event.Disconnected)
        disconnect()
    }
  }
  protected def reconnect() {
    log.trace("reconnect")
    disconnect()
    connect()
  }
  protected def disconnect() = disconnect(false)
  protected def disconnect(silent: Boolean = false) = lock.synchronized {
    if (!silent)
      log.trace("disconnect")
    smack.disconnect()
    if (!history(Bot.Event.Disconnected))
      publish(Bot.Event.Disconnected)
  }
  def dispose() {
    log.trace("dispose")
    disconnect()
  }
  private def heartbeat(): Unit = lock.synchronized {
    if (lock.tryLock()) {
      try {
        if (smack.nick != "" && smack.password != "" && smack.authFailCounter.get < 3 && smack.connection != null) {
          if (history(Bot.Event.Connected) && smack.connection.isConnected() && smack.connection.isAuthenticated()) {
            val presence = smack.connection.getRoster().getPresence(smack.connection.getUser())
            if (presence.getType != Presence.Type.available) {
              log.warn("presence status lost (current status is " + presence + "), recover")
              // create an available presence
              smack.connection.sendPacket(new Presence(Presence.Type.available))
            }
          } else {
            val lastConnecting = history.find(_.isInstanceOf[Bot.Event.Connecting]).getOrElse(Bot.Event.Connecting(0)).asInstanceOf[Bot.Event.Connecting]
            if (System.currentTimeMillis - lastConnecting.timestamp > 60000) {
              if (reconnectFlag.get) {
                log.warn("connection/authentication lost, recover")
                reconnect()
              }
            }
          }
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
  protected def sendMessage(target: String, message: String) = smack.send(target, message)
  private def processMessage(chat: Chat, message: JMessage) {
    try {
      val sender = StringUtils.parseBareAddress(message.getFrom())
      if (!processIncommingMessage.get()) {
        processIncommingMessage.set(true)
        return
      }
      if (sender == smack.nick + "@" + smack.connection.getServiceName)
        return
      val msgseq = message.getBody.split("\n").map(_.trim())
      message.getBody.trim match {
        case message if message.matches("""--- \[.*\] message block ---""") =>
          // empty message block
          val id = message.substring(5, message.length - 19).split("#")
          val hash = (id(0)).toInt + sender.hashCode
          val (blockN, blocksTotal) = id(1).split("/").map(_.toInt).toSeq match { case x => (x.head, x.last) }
          log.debug("[BLOCK] empty block " + blockN + "/" + blocksTotal + " with hash " + hash)
          Request ! Reply(this.hashCode, hash, blockN, blocksTotal, "")
        case message if message.matches("""--- \[[-0-9]+#\d+/\d+\] [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12} ---""") =>
          // detect pong message
          val id = message.substring(5, message.length - 42).split("#")
          val hash = (id(0)).toInt + sender.hashCode
          val (blockN, blocksTotal) = id(1).split("/").map(_.toInt).toSeq match { case x => (x.head, x.last) }
          log.debug("[BLOCK] receive pong " + blockN + "/" + blocksTotal + " with hash " + hash)
          Hexapod(this, sender).foreach(_.entity(this).health = true)
          Request ! Reply(this.hashCode, hash, blockN, blocksTotal, "pong")
        case message if message.matches("""(?s)--- \[[-0-9]+#\d+/\d+\] begin of message block ---.*--- end of message block ---""") =>
          val lines = message.split("\n")
          val id = lines.head.substring(5, lines.head.length - 28).split("#")
          val hash = (id(0)).toInt + sender.hashCode
          val (blockN, blocksTotal) = id(1).split("/").map(_.toInt).toSeq match { case x => (x.head, x.last) }
          log.debug("[BLOCK] receive block " + blockN + "/" + blocksTotal + " with hash " + hash)
          Request ! Reply(this.hashCode, hash, blockN, blocksTotal, lines.drop(1).dropRight(1).mkString("\n"))
        case message =>
          Process ! Process.Message.Command(this, sender, Hexapod(this, sender), message)
      }
    } catch {
      case e => log.warn(e.getMessage(), e)
    }
  }
  private def nick() = uuid.toString
  private def password() = config.getString("archinid.jabber.server." + server + ".password").getOrElse("")
  private def uri() = config.getString("archinid.jabber.server." + server + ".uri").get
  private def port() = config.getInt("archinid.jabber.server." + server + ".port").getOrElse(5222)
  class Bot(val log: Logger) {
    assert(log != null)
    val options = new ConnectionConfiguration(uri(), port())
    var connection = new XMPPConnection(options)
    val authFailCounter = new AtomicInteger(0)
    private val chats = new HashMap[String, (Chat, AtomicLong)] with SynchronizedMap[String, (Chat, AtomicLong)]
    private val connectionListener = new ConnectionListener {
      def connectionClosed() {
        log.trace("connection was closed normally or reconnection process has been aborted")
      }
      def connectionClosedOnError(e: Exception) {
        log.trace("connection was closed due to an exception")
      }
      def reconnectionSuccessful() {
        log.trace("connection has reconnected successfully")
      }
      def reconnectionFailed(e: Exception) {
        log.trace("an attempt to connect to the server has failed")
      }
      def reconnectingIn(seconds: Int) {}
    }
    private val rosterListener = new RosterListener() {
      def entriesDeleted(a: java.util.Collection[String]) = publish(Bot.Event.Other(that.Jabber.Event.RosterEntriesDeleted(a)))
      def entriesUpdated(a: java.util.Collection[String]) = publish(Bot.Event.Other(that.Jabber.Event.RosterEntriesUpdated(a)))
      def entriesAdded(a: java.util.Collection[String]) = publish(Bot.Event.Other(that.Jabber.Event.RosterEntriesAdded(a)))
      def presenceChanged(p: Presence) = publish(Bot.Event.Other(that.Jabber.Event.RosterPresenceChanged(p)))
    }
    private val messageListener = new MessageListener {
      def processMessage(chat: Chat, msg: JMessage) {
        // If this is starting a new chat, then it won't be in the
        // chats Map. So add it and send the clients a NewChat message.
        chats.getOrElse(msg.getFrom(), Nil) match {
          case Tuple2(chat, timestamp) => timestamp.asInstanceOf[AtomicLong].set(System.currentTimeMillis)
          case Nil => chats(msg.getFrom()) = (chat, new AtomicLong(System.currentTimeMillis))
        }
        if (msg.getBody() != null)
          that ! that.Jabber.Event.Message(chat, msg)
        else
          log.warn("skip empty message from " + chat + " with type " + msg.getType() + ", err: " + msg.getError())
      }
    }
    private val chatListener = new ChatManagerListener {
      def chatCreated(chat: Chat, createdLocally: Boolean) {
        if (!createdLocally)
          chat.addMessageListener(messageListener)
      }
    }
    def nick = Stash.nick
    def password = Stash.password
    def connect(nick: String, password: String): Boolean = synchronized {
      if (connection.isConnected)
        connection.disconnect()
      val chatManager = connection.getChatManager
      chats.values.foreach(x => x._1.getListeners.toSeq.foreach(x._1.removeMessageListener(_)))
      chatManager.getChatListeners.toSeq.foreach(chatManager.removeChatListener(_))
      chats.clear()
      connection.removeConnectionListener(connectionListener)
      connection = new XMPPConnection(options)
      /*
       * prepaire
       */
      if (Stash.nick != nick || Stash.password != password)
        authFailCounter.set(0) // reset counter if parameter changed
      Stash.nick = nick
      Stash.password = password
      options.setReconnectionAllowed(false)
      /*
       * connect
       */
      connection.connect
      for (i <- 0 to 50 if (!connection.isConnected))
        Thread.sleep(100)
      if (connection.isConnected) {
        connection.login(nick, password)
        for (i <- 0 to 50 if (!connection.isAuthenticated))
          Thread.sleep(100)
        if (connection.isAuthenticated) {
          /*          connection.addPacketListener(new PacketListener() { def processPacket(p: Packet) = log.debug("INBOUND " + p) },
            new PacketFilter() { def accept(p: Packet): Boolean = { true } })
          connection.addPacketWriterListener(new PacketListener() { def processPacket(p: Packet) = log.debug("OUTBOUND " + p) },
            new PacketFilter() { def accept(p: Packet): Boolean = { true } })*/
          val roster = connection.getRoster()
          // some XMPP server configs do not give you a Roster.
          if (roster != null)
            roster.addRosterListener(rosterListener)
          // manage the remotely created chats, so we don't miss incomming messages
          val chatManager = connection.getChatManager
          chatManager.addChatListener(chatListener)
          roster.setSubscriptionMode(Roster.SubscriptionMode.accept_all)
          connection.addConnectionListener(connectionListener) // step skipped if already in use
          true
        } else {
          authFailCounter.incrementAndGet
          log.warn("authentification failed")
          false
        }
      } else {
        log.warn("connection failed")
        false
      }
    }

    def handle(): Unit = synchronized {
    }
    def disconnect() = synchronized {
      if (connection.isConnected) {
        connection.disconnect()
        for (i <- 0 to 50 if (connection.isConnected))
          Thread.sleep(100)
      }
    }
    def send(target: String, message: String): Unit = {
      if (!connection.isConnected) {
        log.warn("connection lost, message to " + target + " skipped")
        return
      }
      val msg = new JMessage(target, JMessage.Type.chat)
      msg.setBody(message)
      // If there isn't an existing chat in chats, make one and put it there.
      chats.getOrElse(target, Nil) match {
        case Tuple2(chat, timestamp) =>
          chat.asInstanceOf[Chat].sendMessage(msg)
          timestamp.asInstanceOf[AtomicLong].set(System.currentTimeMillis)
        case Nil => {
          val chat = smack.connection.getChatManager().createChat(target, messageListener)
          chats(target) = (chat, new AtomicLong(System.currentTimeMillis))
          chat.sendMessage(msg)
        }
      }
    }
    private object Stash {
      var nick: String = ""
      var password: String = ""
    }
  }
  /*
   * add archinids to roster
   */
  class subscriberHexapodNew() extends Subscriber[org.digimead.documentumelasticus.hexapod.Event, Hexapod.ObjectH#Pub] {
    def notify(pub: Hexapod.ObjectH#Pub, event: org.digimead.documentumelasticus.hexapod.Event): Unit = {
      event match {
        case Hexapod.Event.New(hexapod) if hexapod.isInstanceOf[Archinid] =>
          hexapod.subscribe(new subscriberHexapodAdd(hexapod))
        case _ =>
      }
    }
  }
  class subscriberHexapodAdd(hexapod: Hexapod) extends Subscriber[org.digimead.documentumelasticus.hexapod.Changed, Hexapod#InstanceH#Pub] {
    def notify(pub: Hexapod#InstanceH#Pub, event: org.digimead.documentumelasticus.hexapod.Changed): Unit = {
      if (hexapod.entity.isDefinedAt(that) && hexapod.entity(that).health && hexapod.entity(that).authenticated) {
        hexapod.removeSubscription(this)
        val roster = smack.connection.getRoster()
        if (roster != null) {
          if (roster.getEntries().toSeq.exists(_.getUser() == hexapod.entity(that).target)) {
            log.debug("archinid entry " + hexapod.entity(that).target + " already in roster")
          } else {
            log.info("add archinid entry " + hexapod.entity(that).target + " to roster")
            roster.createEntry(hexapod.entity(that).target, hexapod.entity(that).target, null)
          }
        }
      }
    }
  }

}

trait JabberSingleton {
  object Message {
    case class SetPresence(presence: Presence)
    case class CreateChat(to: String)
    case class SendMsg(to: String, msg: String)
    case class CloseChat(to: String)
    case class GetPendingMsg(to: String)
  }
  object Event {
    case class Message(val chat: Chat, val message: JMessage)
    case class NewRoster(r: Roster) extends jabber.Event
    case class RosterEntriesDeleted[T](entries: Iterable[T]) extends jabber.Event
    case class RosterEntriesUpdated[T](entries: Iterable[T]) extends jabber.Event
    case class RosterEntriesAdded[T](entries: Iterable[T]) extends jabber.Event
    case class RosterPresenceChanged(p: Presence) extends jabber.Event
    case class NewChat(chat: Chat) extends jabber.Event
    case class RecvMsg(chat: Chat, msg: JMessage) extends jabber.Event
    case class BulkMsg(chat: Chat, msg: List[JMessage]) extends jabber.Event
  }
}

/*
        case SetPresence(presence) => connection.sendPacket(presence)
        case CreateChat(to) => {
          val chat: Chat = connection.getChatManager().createChat(to, md)
          chats += (to -> chat)
          this ! NewChat(chat)
        }
        // Send a Message to the XMPP Server
        case SendMsg(to, message) =>
          val msg = new JMessage(to, JMessage.Type.chat)
          msg.setBody(message)
          // If there isn't an existing chat in chats, make one and put it there.
          chats.getOrElse(to, Nil) match {
            case chat: Chat => chat.sendMessage(msg)
            case Nil => {
              val chat = connection.getChatManager().createChat(to, md)
              chats += (to -> chat)
              chat.sendMessage(msg)
            }
          }
        case CloseChat(to) => chats -= to
        /* From here on are Messages we process from the XMPP server */
        case r @ RosterEntriesDeleted(_) =>
        case r @ RosterEntriesUpdated(_) =>
        case r @ RosterEntriesAdded(_) =>
        case r @ RosterPresenceChanged(_) =>
        case c @ NewChat(chat) =>
        // A new Chat has come in from the XMPP server
        case m @ RecvMsg(chat, msg) => {
          // If this is starting a new chat, then it won't be in the
          // chats Map. So add it and send the clients a NewChat message.
          chats.getOrElse(msg.getFrom(), Nil) match {
            case Nil => {
              chats += (msg.getFrom() -> chat)
              this ! NewChat(chat)
            }
            case _ => {}
          }
        }
        /*
	 *
	 */
        case request: Request =>
          log.trace("[SENDMSG] to " + request.target + " " + request.t.message)
	  this ! SendMsg(request.target, request.t.message)
        case m =>
          log.warn("unknown message", m)
      }
  }
  object Message {
    case class RegisterPassword(val newPassword: String)
  }
*/
/*
    def prepare() = synchronized {
    }
*/
/*        case message if (message.startsWith("")) =>
          // detect message block begin
          val id = message.substring(5, message.length - 28).split("#")
          val hash = (id(0)).toInt + sender.hashCode
          val (blockN, blocksTotal) = id(1).split("/").map(_.toInt).toSeq match { case x => (x.head, x.last) }
          log.debug("[BLOCK] begin block " + blockN + "/" + blocksTotal + " with hash " + hash)
          val value = IncompleteBlockValue(hash, blockN, "", System.currentTimeMillis())
          incompleteBlockBuffer(key) = Seq(value)
        case message if (incompleteBlockBuffer.isDefinedAt(key) && message == "") =>
          // detect message block end
          val blocks = incompleteBlockBuffer(key)
          val blockHash = blocks.head.hash
          val blockN = blocks.head.count
          val blocksTotal = 0
          log.debug("[BLOCK] receive block " + blockN + "/" + blocksTotal + " with hash " + blockHash)
          val blockText = blocks.drop(1).map(_.chunk).mkString("\n")
          incompleteBlockBuffer.remove(key)
          Request ! Reply(this.hashCode, blockHash, blockN, blocksTotal, blockText)
        case message if (incompleteBlockBuffer.isDefinedAt(key)) =>
          // detect message block content
          val firstBlock = incompleteBlockBuffer(key).head
          val value = IncompleteBlockValue(firstBlock.hash, firstBlock.count, message, System.currentTimeMillis())
          incompleteBlockBuffer(key) = incompleteBlockBuffer(key) :+ value*/

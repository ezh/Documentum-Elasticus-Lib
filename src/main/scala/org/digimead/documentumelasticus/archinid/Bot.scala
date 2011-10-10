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

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import net.lag.configgy.Config
import org.digimead.documentumelasticus.{ Archinid, Hexapod }
import org.digimead.documentumelasticus.helper.{ OpenPGP, Passwords, Publisher, SubscribeSelf }
import org.digimead.documentumelasticus.helper.openpgp.AsymmetricAlgorithm
import org.slf4j.MDC
import scala.actors.Actor
import scala.actors.Futures.future
import scala.collection.mutable.{ HashMap, HashSet, SynchronizedMap }

package bot {
  sealed trait Event
  sealed trait Message
}

trait Bot extends Actor with Publisher[bot.Event] with SubscribeSelf[bot.Event] {
  val uuid: UUID
  val Pool: PoolSingleton
  val server: String
  val config: Config
  val timeout: Int
  val kind: String
  val reconnectFlag = new AtomicBoolean(true)
  val priority: Int // higher better
  val processIncommingMessage = new AtomicBoolean(true)
  val lastBlockedCommand = new HashMap[String, String] with SynchronizedMap[String, String]
  val targetUUIDMap = new HashMap[String, UUID] with SynchronizedMap[String, UUID]
  private val authenticationLock = new HashMap[String, ReentrantLock] with SynchronizedMap[String, ReentrantLock]
  addRule(classOf[Bot.Event.Connecting], (history: HashSet[bot.Event]) => {
    history(Bot.Event.Connected) = false
    history(Bot.Event.Disconnected) = false
  })
  addRule(Bot.Event.Connected, (history: HashSet[bot.Event]) => {
    history.filter(_.isInstanceOf[Bot.Event.Connecting]).foreach(history.removeEntry(_))
    history(Bot.Event.Disconnected) = false
  })
  addRule(Bot.Event.Disconnected, (history: HashSet[bot.Event]) => {
    history.filter(_.isInstanceOf[Bot.Event.Connecting]).foreach(history.removeEntry(_))
    history(Bot.Event.Connected) = false
  })
  protected def connect()
  protected def reconnect()
  protected def disconnect()
  def notify(event: bot.Event) = {
    try {
      val skip = Seq(
        """please enter password, salt .*""",
        "authentication successful",
        "authentication failed")
      event match {
        case Bot.Event.Connecting(timestamp) =>
        case Bot.Event.Connected =>
          Hexapod.hexapod.values.foreach(_.entity.get(this).foreach(e => {
            e.authenticated = false
            e.authenticationAttempt = 0
          }))
        case Bot.Event.Disconnected =>
          Hexapod.hexapod.values.foreach(_.entity.get(this).foreach(e => {
            e.authenticated = false
            e.authenticationAttempt = 0
          }))
        case Bot.Event.UserEnter(target) =>
          Hexapod.hexapod.values.foreach(_.entity.get(this).foreach(e => {
            if (e.target == target) {
              e.authenticated = false
              e.authenticationAttempt = 0
              authenticate(e.target)
            }
          }))
        case Bot.Event.UserLeave(target) =>
          Hexapod.hexapod.values.foreach(_.entity.get(this).foreach(e => {
            if (e.target == target) {
              e.authenticated = false
              e.authenticationAttempt = 0
            }
          }))
        case Bot.Event.UserArchinid(target) =>
          if (Hexapod(this, target) == None) {
            future {
              try {
                log.info("archinid candidate " + target + " detected")
                waitEvent(Bot.Event.Connected, timeout, true)
                if (!Pool.exists(this, target))
                  Pool.addNode(this, target)
              } catch { case e => log.error(e.getMessage, e) }
            }
          }
        case Bot.Event.Other(_) =>
        case command @ Bot.Event.SendCommand(target, message) =>
          log.info("send command " + message + " to " + target)
        case Bot.Event.SendMessage(_, _) =>
        case Bot.Event.ReceiveMessage(target, "please authenticate first") =>
          log.debug("skip message \"please authenticate first\"")
          processIncommingMessage.set(false)
          if (targetUUIDMap.isDefinedAt(target) && Hexapod.locked.isDefinedAt(targetUUIDMap(target)))
            future {
              try {
                authenticate(target)
              } catch {
                case e => log.warn(e.getMessage, e)
              }
            }
          else
            log.trace("skip authentication against unknown target " + target)
        case Bot.Event.ReceiveMessage(target, "please register first") =>
          log.debug("skip message \"please register first\"")
          processIncommingMessage.set(false)
          future {
            try {
              register(target, null)
            } catch {
              case e => log.warn(e.getMessage, e)
            }
          }
        case Bot.Event.ReceiveMessage(target, msg) => {
          if (msg.matches("""--- \[[-0-9]+#\d+/\d+\] [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12} ---""")) {
            // ping pong message; update uuid for target
            if (!targetUUIDMap.isDefinedAt(target))
              try {
                val uuidString = msg.dropRight(4).takeRight(36)
                log.debug("confront target " + target + " with uuid " + uuidString)
                targetUUIDMap(target) = UUID.fromString(uuidString)
              } catch {
                case e => log.warn(e.getMessage, e)
              }
          }
          if (skip.exists(msg.matches(_)))
            processIncommingMessage.set(false)
        }
      }
    } catch {
      case e => log.error(e.getMessage, e)
    }
  }
  def ping(target: String): Boolean
  def command(target: String, message: String): Unit = command(target, message, false)
  def command(target: String, message: String, force: Boolean): Unit = {
    val allow = message.trim == "ping" || force || (Hexapod(this, target) match {
      case Some(hexapod) =>
        hexapod.isAllowCommand(this, message.trim)
      case None =>
        lastBlockedCommand(target) = message.trim
        authenticate(target)
        false
    })
    if (allow) {
      publish(Bot.Event.SendCommand(target, message.trim))
      send(target, message.trim)
    } else {
      log.warn("send command " + message + " to " + target + " forbidden")
    }
  }
  def command(hexapod: Hexapod, message: String): Unit = command(hexapod, message, false)
  def command(hexapod: Hexapod, message: String, force: Boolean): Unit = hexapod.getTarget(this) match {
    case Some(target) => command(target, message, force)
    case None => log.warn("target for bot " + this + " in hexapod entities (" + hexapod.entity.mkString(", ") + ") nof found")
  }
  def send(target: String, message: String): Unit = {
    publish(Bot.Event.SendMessage(target, message.trim))
    sendMessage(target, message.trim)
  }
  def send(hexapod: Hexapod, message: String): Unit = hexapod.getTarget(this) match {
    case Some(target) => send(target, message)
    case None => log.warn("target for hexapod " + hexapod + " nof found")
  }
  def dispose()
  protected def sendMessage(target: String, message: String): Unit
  def authenticate(target: String): Unit = {
    future {
      if (!authenticationLock.isDefinedAt(target))
        authenticationLock(target) = new ReentrantLock()
      try {
        if (authenticationLock(target).tryLock()) {
          Request(target, this, Request.Message.Ping).setLogger(log).setTimeout(timeout).send[Option[Any]] match {
            case Some(_) =>
              val hexapod: Hexapod = if (!targetUUIDMap.isDefinedAt(target) || !Hexapod.locked.isDefinedAt(targetUUIDMap(target))) {
                log.trace("skip authentication against unknown target " + target)
                null
              } else {
		MDC.put("UUID" , targetUUIDMap(target).toString())
                (Hexapod(targetUUIDMap(target)) match {
                  case Some(hexapod) =>
                    if (hexapod.entity.isDefinedAt(this) && hexapod.entity(this).target != target) {
                      log.error("break-in attempt was detected. illegal target " + target + " try to replace " + hexapod.entity(this).target + " " + hexapod.uuid)
                      null
                    } else {
                      if (hexapod.entity.isDefinedAt(this) && hexapod.entity(this).authenticationAttempt >= 3) {
                        log.info("skip authentication against " + target + " attempt IRC #" + hexapod.entity(this).authenticationAttempt)
                        null
                      } else {
                        log.info("authenticate against " + target + " attempt IRC #" + (if (hexapod.entity.isDefinedAt(this)) hexapod.entity(this).authenticationAttempt else 0))
                        hexapod
                      }
                    }
                  case None => new Hexapod(targetUUIDMap(target))
                })
              }
              if (hexapod != null) {
                hexapod.addOrUpdate(this, target)
                var successful = false
                command(target, "login " + uuid.toString + " " + (new Passwords() {}).generate(16), true)
                var salt: String = null
                var peerUUID: String = null
                val matcher: PartialFunction[bot.Event, Unit] = {
                  case Bot.Event.ReceiveMessage(sender, msg) if (msg.matches("""please enter password, salt .*""") && sender == target) =>
                    salt = msg.drop(28)
                  case Bot.Event.ReceiveMessage(sender, msg) if (msg.matches("""please register first""") && sender == target) =>
                  // registration begin from notification handler
                }
                waitEvent(matcher, timeout)
                if (salt != null) {
                  log.debug("salt " + salt)
                  // decrypt
                  val randomDec = new SecureRandom
                  val cipherDec = Cipher.getInstance(AsymmetricAlgorithm(Hexapod.unlocked.encriptionKey.publicKey.getAlgorithm()) + "/ECB/PKCS1Padding", "BC")
                  cipherDec.init(Cipher.DECRYPT_MODE, Hexapod.unlocked.encriptionKey.privateKey.getKey(), randomDec)
                  val plain: Array[Byte] = OpenPGP.decode64(salt).map(cipherDec.doFinal(_)).get
                  log.debug("unencrypter salt " + new String(plain))
                  // encrypt
                  val randomEnc = new SecureRandom
                  val cipherEnc = Cipher.getInstance(AsymmetricAlgorithm(Hexapod.locked(hexapod.uuid).encriptionKey.publicKey.getAlgorithm()) + "/ECB/PKCS1Padding", "BC")
                  cipherEnc.init(Cipher.ENCRYPT_MODE, Hexapod.locked(hexapod.uuid).encriptionKey.publicKey.getKey("BC"), randomEnc)
                  OpenPGP.encode64(cipherEnc.doFinal(plain)).foreach(str => {
                    command(target, "password " + str, true)
                    val matcher: PartialFunction[bot.Event, Unit] = {
                      case Bot.Event.ReceiveMessage(sender, "authentication successful") if (sender == target) =>
                        successful = true
                      case Bot.Event.ReceiveMessage(sender, "authentication failed") if (sender == target) =>
                        successful = false
                    }
                    waitEvent(matcher, timeout)
                  })
                } else {
                  log.warn("salt not found")
                }
                if (successful) {
                  log.info("authentication has been successful (" + hexapod.uuid + ")")
                  hexapod.addOrUpdate(this, target, Some(true))
                  // resend last command
                  if (lastBlockedCommand.isDefinedAt(target))
                    send(target, lastBlockedCommand(target))
                } else {
                  log.warn("authentication has been failed (" + hexapod.uuid + ")")
                  hexapod.entity(this).authenticationAttempt += 1
                }
              }
            case None =>
              log.warn(target + " unreachable via " + this)
          }
        } else {
          log.debug("skip authentification becase of lock for " + target)
        }
      } catch {
        case e => log.error(e.getMessage, e)
      } finally {
        if (authenticationLock(target).isHeldByCurrentThread())
          authenticationLock(target).unlock()
	MDC.clear()
      }
    }
  }
  def register(target: String, targetUUID: UUID): Unit = {
    import org.bouncycastle.util.encoders.Base64._
    if (!targetUUIDMap.isDefinedAt(target)) {
      log.warn("uuid for target " + target + " not found")
      return
    }
    log.info("register against " + target + " with uuid " + targetUUIDMap(target))
    if (!Hexapod.locked.isDefinedAt(targetUUIDMap(target))) {
      log.warn("registration process aborted, public key for " + targetUUIDMap(target) + " not found")
      return
    }
    future {
      try {
        val message = "register " + Hexapod.uuid + " " + new String(encode(OpenPGP.encrypt(Hexapod.locked(targetUUIDMap(target)), Hexapod.unlocked.getEncoded)))
        val registerRequest = Request(target, this, Request.Message.Simple(message)).setLogger(log).setForce(true).setTimeout(timeout)
        registerRequest.send[Option[String]] match {
          case Some(str) if (str == "registration successful") =>
            log.info("registration successful")
            authenticate(target)
          case _ =>
            log.warn("registration failed")
        }
      } catch {
        case e => log.error(e.getMessage, e)
      }
    }
  }
}

object Bot {
  object Message {
    import org.digimead.documentumelasticus.archinid.bot.{ Message => M }
    case object Connect extends M
    case object Disconnect extends M
    case object Reconnect extends M
    case object Heartbeat extends M
  }
  object Event {
    import org.digimead.documentumelasticus.archinid.bot.{ Event => E }
    case class Connecting(val timestamp: Long) extends E
    case object Connected extends E
    case object Disconnected extends E
    case class UserEnter(val target: String) extends E
    case class UserLeave(val target: String) extends E
    case class UserArchinid(val target: String) extends E
    case class SendCommand(val target: String, val message: String) extends E
    case class SendMessage(val target: String, val message: String) extends E
    case class ReceiveMessage(val target: String, val message: String) extends E
    case class Other(val value: Any) extends E
  }
}

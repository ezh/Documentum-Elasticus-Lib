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

package org.digimead.documentumelasticus.helper.openpgp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringBufferInputStream
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.slf4j.LoggerFactory

sealed trait lockState
case class Unlocked() extends lockState
case class Locked() extends lockState

class KeySet[lockState](val signingKey: Key[lockState], val encriptionKey: Key[lockState]) {
  type state = lockState
  private val log = LoggerFactory.getLogger(getClass.getName)
  def load(input: InputStream) {
  }
  def getEncoded(): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    signingKey.secretKey.encode(output)
    encriptionKey.secretKey.encode(output)
    return output.toByteArray()
  }
  def getEncoded64(): String = {
    import org.bouncycastle.util.encoders.Base64._
    new String(encode(getEncoded()))
  }
  override def toString() = {
    String.format("[sign: %s, encript: %s]", signingKey, encriptionKey)
  }
  def unlock(passPhrase: String) = {
    log.debug("unlock keyset " + this)
    val unlockedSigningKey = signingKey.unlock(passPhrase)
    val unlockedEncriptionKey = encriptionKey.unlock(passPhrase)
    new KeySet[Unlocked](unlockedSigningKey, unlockedEncriptionKey)
  }
}

object KeySet {
  private val log = LoggerFactory.getLogger(getClass.getName)
  def apply(keyRing: PGPSecretKeyRing): KeySet[Locked] = {
    log.debug("load keyset from PGPSecretKeyRing")
    val it = keyRing.getSecretKeys()
    val signingKey: Key[Locked] = if (it.hasNext()) {
      new Key[Locked](it.next().asInstanceOf[PGPSecretKey])
    } else {
      val message = "signing/master key not found"
      log.error(message)
      throw new IllegalArgumentException(message)
    }
    val encriptionKey: Key[Locked] = if (it.hasNext())
      new Key[Locked](it.next().asInstanceOf[PGPSecretKey], signingKey.secretKey)
    else {
      val message = "signing/master key not found"
      log.error(message)
      throw new IllegalArgumentException(message)
    }
    new KeySet[Locked](signingKey, encriptionKey)
  }
  def apply(input: InputStream): KeySet[Locked] = {
    val keyRing = new PGPSecretKeyRing(input)
    input.close()
    apply(keyRing)
  }
  def apply(encoded: Array[Byte]): KeySet[Locked] = apply(new ByteArrayInputStream(encoded))
  def apply(encoded: String): KeySet[Locked] = {
    import org.bouncycastle.util.encoders.Base64._
    apply(decode(encoded))
  }
}

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
import org.bouncycastle.openpgp.PGPPrivateKey

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._

class Key[lockState](val secretKey: PGPSecretKey,
  val privateKey: PGPPrivateKey,
  val masterKey: PGPSecretKey,
  val requiredSignatureType: SignatureType.Value)(implicit mf: Manifest[lockState]) {
  type state = lockState
  private val log = LoggerFactory.getLogger(getClass.getName)
  val publicKey = secretKey.getPublicKey()
  val signature = getSignature(masterKey, requiredSignatureType)
  //  val flags = signature.map(_.getKeyFlags())
  def this(secretKey: PGPSecretKey, signingKey: PGPSecretKey)(implicit mf: Manifest[lockState]) = {
    this(secretKey, null, signingKey, SignatureType.SUBKEY_BINDING)
    log.debug("try to create locked key " + this)
    checkLockedKey()
    checkSignature()
    log.debug("successful")
  }
  def this(secretKey: PGPSecretKey)(implicit mf: Manifest[lockState]) = {
    this(secretKey, null, secretKey, SignatureType.POSITIVE_CERTIFICATION)
    log.debug("try to create locked self-signed key " + this)
    checkLockedKey()
    checkSignature()
    log.debug("successful")
  }
  def this(secretKey: PGPSecretKey, privateKey: PGPPrivateKey)(implicit mf: Manifest[lockState]) = {
    this(secretKey, privateKey, secretKey, SignatureType.POSITIVE_CERTIFICATION)
    if (mf.toString + "$" != Unlocked.getClass.getName()) {
      val message = "try to create locked key with private key"
      log.error(message)
      throw new IllegalArgumentException(message)
    }
    log.debug("create unlocked self-signed key")
  }
  def load(key: PGPSecretKey) {
  }
  def getUserID() = getUserIDs.head
  def getUserIDs() = masterKey.getUserIDs().toSeq.map(_.asInstanceOf[String])
  def verify(masterKey: Key[_]): Boolean = signature match {
    case Some(signature) => signature.verifyCertification(this, masterKey)
    case None =>
      log.warn("signature not found")
      false
  }
  def unlock(passPhrase: String) = {
    log.debug("unlock key " + this)
    val privateKey = secretKey.extractPrivateKey(passPhrase.toCharArray(), "BC");
    new Key[Unlocked](secretKey, privateKey, masterKey, requiredSignatureType);
  }
  def getKeyID() = secretKey.getKeyID()
  def getSize() = publicKey.getBitStrength()
  def getAlgorithm() = AsymmetricAlgorithm(publicKey.getAlgorithm())
  def getHumanKeyID() = "%08X".format(secretKey.getKeyID())
  override def toString() = "%d-%s/%s".format(getSize(), getAlgorithm(), getHumanKeyID())
  private def getSignature(signingKey: PGPSecretKey, requiredSignatureType: SignatureType.Value): Option[KeySignature] = {
    val signatures = publicKey.getSignatures();
    while (signatures.hasNext()) {
      val signature = new KeySignature(signatures.next().asInstanceOf[PGPSignature])
      if ((signature.getKeyID() == signingKey.getKeyID()) && (signature.getSignatureType() == requiredSignatureType)) {
        return Some(signature)
      }
    }
    None
  }
  private def checkLockedKey() {
    if (mf.toString + "$" != Locked.getClass.getName()) {
      val message = "try to create unlocked key without private key"
      log.error(message)
      throw new IllegalArgumentException(message)
    }
  }
  private def checkSignature() {
    val result = signature match {
      case Some(signature) => signature.verifyCertification(this, masterKey)
      case None =>
        log.warn("signature not found")
        false
    }
    if (!result) {
      val message = if (secretKey == masterKey)
        "not a self-signed master key"
      else
        "not a valid subkey"
      log.error(message)
      throw new IllegalArgumentException(message)
    }
  }
}

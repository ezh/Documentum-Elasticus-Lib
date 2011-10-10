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

import java.security.SignatureException
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.{PGPException, PGPSignature, PGPSignatureSubpacketVector}
import org.slf4j.LoggerFactory

class KeySignature(val signature: PGPSignature) {
  private val log = LoggerFactory.getLogger(getClass.getName)
  private val subpackets: PGPSignatureSubpacketVector = signature.getHashedSubPackets()
  /** Returns the type of signature {@code this} is. */
  def getSignatureType(): SignatureType.Value = SignatureType(signature.getSignatureType())
  /** Returns the {@link HashAlgorithm} used to make the signature. */
  def getHashAlgorithm(): HashAlgorithm.Value = HashAlgorithm(signature.getHashAlgorithm())
  /** Returns the timestamp at which the signature was made. */
  //        def getCreatedAt(): DateTime = new DateTime(signature.getCreationTime(), DateTimeZone.UTC)
  /** Returns the {@link AsymmetricAlgorithm} used to make the signature. */
  def getKeyAlgorithm(): AsymmetricAlgorithm.Value = AsymmetricAlgorithm(signature.getKeyAlgorithm())
  /** Returns the key ID of the key that made the signature. */
  def getKeyID() = signature.getKeyID()
  /** Returns the {@link KeyFlag}s asserted by the signature. */
  //        def getKeyFlags = return IntegerEquivalents.fromBitmask(KeyFlag.class, subpackets.getKeyFlags())
  /** Returns a list of the preferred {@link SymmetricAlgorithm}s of the key. */
  //def getPreferredSymmetricAlgorithms() = return IntegerEquivalents.fromIntArray(SymmetricAlgorithm.class, subpackets.getPreferredSymmetricAlgorithms())
  /** Returns a list of the preferred {@link CompressionAlgorithm}s of the key. */
  //        def getPreferredCompressionAlgorithms() = return IntegerEquivalents.fromIntArray(CompressionAlgorithm.class, subpackets.getPreferredCompressionAlgorithms())
  /** Returns a list of the preferred {@link HashAlgorithm}s of the key. */
  //        def getPreferredHashAlgorithms() = return IntegerEquivalents.fromIntArray(HashAlgorithm.class, subpackets.getPreferredHashAlgorithms())
  /**
   * Verify this signature for a self-signed {@link MasterKey}.
   *
   * @param key a self-signed master key
   * @return {@code true} if the signature is valid, {@code false} otherwise
   */
  def verifyCertification(key: Key[_]): Boolean = {
    try {
      signature.initVerify(key.publicKey, "BC")
      signature.verifyCertification(key.getUserID, key.publicKey)
    } catch {
      case e: PGPException =>
	log.warn(e.getMessage, e)
	false
      case e: SignatureException =>
	log.warn(e.getMessage, e)
	false
      case e: Exception => throw new RuntimeException(e)
    }
  }
  /**
   * Verify this signature for a {@link SubKey} signed by a {@link MasterKey}.
   *
   * @param key a subkey
   * @param masterKey the signing master key
   * @return {@code true} if the signature is valid, {@code false} otherwise
   */
  def verifyCertification(key: Key[_], masterKey: Key[_]): Boolean = verifyCertification(key, masterKey.publicKey)
  def verifyCertification(key: Key[_], masterKey: PGPSecretKey): Boolean = verifyCertification(key, masterKey.getPublicKey())
  def verifyCertification(key: Key[_], masterKey: PGPPublicKey): Boolean = {
    if (masterKey == key.publicKey) // self-signed a bit different
      return verifyCertification(key)
    try {
      signature.initVerify(masterKey, "BC")
      signature.verifyCertification(masterKey, key.publicKey)
    } catch {
      case e: PGPException =>
	log.warn(e.getMessage, e)
	false
      case e: SignatureException =>
	log.warn(e.getMessage, e)
	false
      case e: Exception => throw new RuntimeException(e)
    }
  }
}

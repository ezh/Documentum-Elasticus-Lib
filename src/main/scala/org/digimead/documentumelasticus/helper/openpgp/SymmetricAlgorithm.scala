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

import javax.crypto.Cipher
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.slf4j.LoggerFactory

object SymmetricAlgorithm extends Enumeration {
  private val log = LoggerFactory.getLogger(getClass.getName)
  type SymmetricAlgorithm = Value
  /**
   * Plaintext or unencrypted data
   *
   * @deprecated Do not store unencrypted data.
   */
  val PLAINTEXT = Value(SymmetricKeyAlgorithmTags.NULL, "Plaintext")
  /**
   * IDEA
   *
   * @deprecated Encumbered by patents.
   */
  val IDEA = Value(SymmetricKeyAlgorithmTags.IDEA, "IDEA")
  /**
   * TripleDES (DES-EDE, 168 bit key derived from 192)
   *
   * @deprecated Replaced by AES.
   */
  val TRIPLE_DES = Value(SymmetricKeyAlgorithmTags.TRIPLE_DES, "3DES")
  /**
   * CAST-128 (also known as CAST5)
   *
   * @deprecated
   * @see <a href="http://www.ietf.org/rfc/rfc2144.txt">RFC 2144</a>
   */
  val CAST_128 = Value(SymmetricKeyAlgorithmTags.CAST5, "CAST-128")
  /**
   * Blowfish (128 bit key, 16 rounds)
   *
   * @deprecated
   */
  val BLOWFISH = Value(SymmetricKeyAlgorithmTags.BLOWFISH, "Blowfish")
  /**
   * SAFER-SK (128 bit key, 13 rounds)
   *
   * @deprecated Not specified by RFC 4880.
   */
  val SAFER_SK = Value(SymmetricKeyAlgorithmTags.SAFER, "SAFER-SK")
  /**
   * DES (56 bit key)
   *
   * @deprecated Not specified by RFC 4880.
   */
  val DES = Value(SymmetricKeyAlgorithmTags.DES, "DES")
  /**
   * AES with 128-bit key
   */
  val AES_128 = Value(SymmetricKeyAlgorithmTags.AES_128, "AES-128")
  /**
   * AES with 192-bit key
   */
  val AES_192 = Value(SymmetricKeyAlgorithmTags.AES_192, "AES-192")
  /**
   * AES with 256-bit key
   */
  val AES_256 = Value(SymmetricKeyAlgorithmTags.AES_256, "AES-256")
  /**
   * Twofish with 256-bit key
   *
   * @deprecated
   */
  val TWOFISH = Value(SymmetricKeyAlgorithmTags.TWOFISH, "Twofish")
  /**
   * The default symmetric algorithm to use.
   */
  val DEFAULT = if (Cipher.getMaxAllowedKeyLength("AES") == 128) {
    log.warn("unrestricted policy files have not been installed for this runtime, symmetric algorithm set to " + AES_128)
    AES_128
  } else {
    AES_256
  }
  /**
   * A list of symmetric algorithms which are acceptable for use in Grendel.
   */
  val ACCEPTABLE_ALGORITHMS = Seq(AES_128, AES_192, AES_256)
}

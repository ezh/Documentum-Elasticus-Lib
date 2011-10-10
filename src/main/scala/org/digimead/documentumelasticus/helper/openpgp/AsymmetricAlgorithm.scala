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

import java.security.spec.AlgorithmParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags

object AsymmetricAlgorithm extends Enumeration {
  type AsymmetricAlgorithm = Value
  /**
   * Elgamal (Encrypt-Only)
   *
   * @see <a href="http://en.wikipedia.org/wiki/ElGamal_encryption">Wikipedia</a>
   */
  val ELGAMAL = Value(PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT, "ElGamal")
  /**
   * DSA (Digital Signature Algorithm)
   *
   * @see <a href="http://en.wikipedia.org/wiki/Digital_Signature_Algorithm">Wikipedia</a>
   */
  val DSA = Value(PublicKeyAlgorithmTags.DSA, "DSA")
  /**
   * RSA (Encrypt or Sign)
   *
   * @see <a href="http://en.wikipedia.org/wiki/RSA">Wikipedia</a>
   */
  val RSA = Value(PublicKeyAlgorithmTags.RSA_GENERAL, "RSA")
  /**
   * RSA Encrypt-Only
   *
   * @deprecated Sign-only keys must be expressed with subpackets in v4 keys.
   * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 13.5, RFC 4880</a>
   */
  val RSA_E = Value(PublicKeyAlgorithmTags.RSA_ENCRYPT, "RSA(e)")
  /**
   * RSA Sign-Only
   *
   * @deprecated Sign-only keys must be expressed with subpackets in v4 keys.
   * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 13.5, RFC 4880</a>
   */
  val RSA_S = Value(PublicKeyAlgorithmTags.RSA_SIGN, "RSA(s)")
  /**
   * Elliptic Curve
   *
   * @deprecated Underspecified in RFC 4880.
   * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 13.8, RFC 4880</a>
   */
  val EC = Value(PublicKeyAlgorithmTags.EC, "EC")
  /**
   * Elliptic Curve Digital Signature Algorithm.
   *
   * @deprecated Underspecified in RFC 4880.
   * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 13.8, RFC 4880</a>
   */
  val ECDSA = Value(PublicKeyAlgorithmTags.ECDSA, "ECDSA")
  /**
   * Elgamal (Encrypt or Sign)
   *
   * @deprecated Prohibited by RFC 4880 due to vulnerabilities.
   * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 13.8, RFC 4880</a>
   * @see <a href="http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.45.3347">Generating ElGamal signatures without knowing the secret key; Daniel Bleichenbacher</a>
   */
  val ELGAMAL_G = Value(PublicKeyAlgorithmTags.ELGAMAL_GENERAL, "ElGamal(g)")
  /**
   * Diffie-Hellman (X9.42, as defined for IETF-S/MIME)
   *
   * @deprecated Underspecified in RFC 4880.
   * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 13.8, RFC 4880</a>
   */
  val DH = Value(PublicKeyAlgorithmTags.DIFFIE_HELLMAN, "DH")
  /**
   * The default asymmetric encryption algorithm, to be used when generating
   * new subkeys.
   */
  val ENCRYPTION_DEFAULT = RSA
  /**
   * The default digital signature algorithm, to be used when generating new
   * master keys.
   *
   */
  val SIGNING_DEFAULT = RSA
  /**
   * Returns the {@link java.security.spec.AlgorithmParameterSpec} required to
   * generate keys for this algorithm.
   */
  def getAlgorithmParameterSpec(value: Value): AlgorithmParameterSpec = value match {
    case ELGAMAL => PregeneratedDHParameterSpec
    case DSA => PregeneratedDSAParameterSpec
    case RSA => new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)
    case RSA_E => null
    case RSA_S => null
    case EC => null
    case ECDSA => null
    case ELGAMAL_G => null
    case DH => null
  }
}

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
import org.bouncycastle.bcpg.HashAlgorithmTags

import org.bouncycastle.openpgp.PGPSignature

/**
 * A hash algorithm for OpenPGP messages.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 9.4, RFC 4880</a>
 */
object HashAlgorithm extends Enumeration {
  type HashAlgorithm = Value
  /**
   * MD5
   *
   * @deprecated Prohibited by RFC 4880, thoroughly broken.
   * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 14, RFC 4880</a>
   * @see <a href="http://eprint.iacr.org/2006/105">Tunnels in Hash Functions: MD5 Collisions Within a Minute</a>
   */
  val MD5 = Value(HashAlgorithmTags.MD5, "MD5")
  /**
   * SHA-1
   * @deprecated Unsuitable for usage in new systems.
   * @see <a href="http://eurocrypt2009rump.cr.yp.to/837a0a8086fa6ca714249409ddfae43d.pdf">SHA-1 collisions now 2⁵²</a>
   */
  val SHA_1 = Value(HashAlgorithmTags.SHA1, "SHA-1")
  /**
   * RIPEMD-160
   *
   * @deprecated Based on same design as {@link #MD5} and {@link #SHA_1}.
   */
  val RIPEMD_160 = Value(HashAlgorithmTags.RIPEMD160, "RIPEMD-160")
  /**
   * Double-width SHA-1
   *
   * @deprecated Not specified by RFC 4880. Only used by CKT builds of PGP.
   * @see <a href="http://www.ietf.org/rfc/rfc2440.txt">RFC 2440</a>
   */
  val DOUBLE_SHA = Value(HashAlgorithmTags.DOUBLE_SHA, "2xSHA-1")
  /**
   * MD2
   *
   * @deprecated Not specified by RFC 4880. Only used by CKT builds of PGP.
   * @see <a href="http://www.ietf.org/rfc/rfc2440.txt">RFC 2440</a>
   */
  val MD2 = Value(HashAlgorithmTags.MD2, "MD2")
  /**
   * TIGER-192
   *
   * @deprecated Not specified by RFC 4880. Only used by CKT builds of PGP.
   * @see <a href="http://www.ietf.org/rfc/rfc2440.txt">RFC 2440</a>
   */
  val TIGER_192 = Value(HashAlgorithmTags.TIGER_192, "TIGER-192")
  /**
   * HAVAL-5-160
   *
   * @deprecated Not specified by RFC 4880. Only used by CKT builds of PGP.
   * @see <a href="http://www.ietf.org/rfc/rfc2440.txt">RFC 2440</a>
   */
  val HAVAL_5_160 = Value(HashAlgorithmTags.HAVAL_5_160, "HAVAL-5-160")
  /**
   * SHA-224
   *
   * Use only for DSS compatibility.
   */
  val SHA_224 = Value(HashAlgorithmTags.SHA224, "SHA-224")
  /**
   * SHA-256
   */
  val SHA_256 = Value(HashAlgorithmTags.SHA256, "SHA-256")
  /**
   * SHA-384
   *
   * Use only for DSS compatibility.
   */
  val SHA_384 = Value(HashAlgorithmTags.SHA384, "SHA-384")
  /**
   * SHA-512
   */
  val SHA_512 = Value(HashAlgorithmTags.SHA512, "SHA-512")
  /**
   * The default hash algorithm to use.
   */
  val DEFAULT = SHA_512
  /**
   * A list of hash algorithms which are acceptable for use in Grendel.
   */
  val ACCEPTABLE_ALGORITHMS = Seq(SHA_224, SHA_256, SHA_384, SHA_512)
}

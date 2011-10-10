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
import org.bouncycastle.bcpg.CompressionAlgorithmTags

/**
 * A compression algorithm for OpenPGP messages.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 9.3, RFC 4880</a>
 */
object CompressionAlgorithm extends Enumeration {
  type CompressionAlgorithm = Value
  /**
   * Uncompressed
   *
   * @deprecated Leaves messages vulnerable to adaptive chosen-plaintext
   *             attacks.
   * @see <a href="http://www.cs.umd.edu/~jkatz/papers/pgp-attack.pdf">Implementation of Chosen-Ciphertext Attacks against PGP and GnuPG</a>
   */
  @Deprecated
  val NONE = Value(CompressionAlgorithmTags.UNCOMPRESSED, "None")
  /**
   * ZLIB
   *
   * @see <a href="http://www.ietf.org/rfc/rfc1951.txt">RFC 1951</a>
   */
  val ZLIB = Value(CompressionAlgorithmTags.ZLIB, "ZLIB")
  /**
   * ZIP
   *
   * @see <a href="http://www.ietf.org/rfc/rfc1950.txt">RFC 1950</a>
   */
  val ZIP = Value(CompressionAlgorithmTags.ZIP, "ZIP")
  /**
   * BZip2
   *
   * @see <a href="http://www.bzip.org/">bzip.org</a>
   */
  val BZIP2 = Value(CompressionAlgorithmTags.BZIP2, "BZIP2")
  /**
   * The default compression algorithm to use.
   */
  val DEFAULT = ZLIB
}

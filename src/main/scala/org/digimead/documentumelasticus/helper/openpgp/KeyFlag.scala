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

object KeyFlag extends Enumeration {
  type KeyFlag = Value
  // org.bouncycastle.openpgp.PGPKeyFlags is incomplete, and thus not
  // referenced here.
  /**
   * Indicates that the key can be used to certify other keys.
   */
  val CERTIFICATION = Value(0x01, "certifying other keys")
  /**
   * Indicates that the key can be used to sign other keys.
   */
  val SIGNING = Value(0x02, "signing data")
  /**
   * Indicates that the key can be used to encrypt communications and storage.
   *
   * <b>N.B.:</b> This includes both {@code 0x04}—"this key may be used to
   * encrypt communications"—and {@code 0x08}—"this key may be used to encrypt
   * storage."
   */
  val ENCRYPTION = Value(0x04 | 0x08, "encrypting data")
  /**
   * Indicates that the key may be split via a secret-sharing mechanism.
   */
  val SPLIT = Value(0x10, "may be split via secret-sharing mechanism")
  /**
   * Indicates that the key can be used for authentication.
   */
  val AUTHENTICATION = Value(0x20, "authentication")
  /**
   * Indicates that the private components of the key may be in the possession
   * of more than one person.
   */
  val SHARED = Value(0x80, "may be in the possession of more than one person")
  /**
   * The default key flags for a master key.
   */
  val MASTER_KEY_DEFAULTS = Seq(SIGNING, AUTHENTICATION, SPLIT)
  /**
   * The default key flags for a sub key.
   */
  val SUB_KEY_DEFAULTS = Seq(ENCRYPTION, SPLIT)
}

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

import org.bouncycastle.openpgp.PGPSignature

/**
 * A type of signature in an OpenPGP message.
 * @see <a href="http://www.ietf.org/rfc/rfc4880.txt">Section 5.2.1, RFC 4880</a>
 */
object SignatureType extends Enumeration {
  type SignatureType = Value
  /**
   * A signature of a binary document.
   *
   * This means the signer owns it, created it, or certifies that it has not
   * been modified.
   */
  val BINARY_DOCUMENT = Value(PGPSignature.BINARY_DOCUMENT, "binary document")
  /**
   * A signature of a canonical text document.
   *
   * This means the signer owns it, created it, or certifies that it has not
   * been modified.  The signature is calculated over the text data with its
   * line endings converted to {@code 0x0D 0x0A} ({@code CR+LF}).
   */
  val TEXT_DOCUMENT = Value(PGPSignature.CANONICAL_TEXT_DOCUMENT, "text document")
  /**
   * A signature of only its own subpacket contents.
   */
  val STANDALONE = Value(PGPSignature.STAND_ALONE, "standalone")
  /**
   * A signature indicating the signer does not make any particular assertion
   * as to how well the signer has checked that the owner of the key is in
   * fact the person described by the User ID.
   */
  val DEFAULT_CERTIFICATION = Value(PGPSignature.DEFAULT_CERTIFICATION, "default certification")
  /**
   * A signature indicating the signer has not done any verification of
   * the signed key's claim of identity.
   */
  val NO_CERTIFICATION = Value(PGPSignature.NO_CERTIFICATION, "no certification")
  /**
   * A signature indicating the signer has done some casual verification of
   * the signed key's claim of identity.
   */
  val CASUAL_CERTIFICATION = Value(PGPSignature.CASUAL_CERTIFICATION, "casual certification")
  /**
   * A signature indicating the signer has done substantial verification of
   * the signed key's claim of identity.
   */
  val POSITIVE_CERTIFICATION = Value(PGPSignature.POSITIVE_CERTIFICATION, "positive certification")
  /**
   * A signature by the top-level signing key indicating that it owns the
   * signed subkey.
   */
  val SUBKEY_BINDING = Value(PGPSignature.SUBKEY_BINDING, "subkey binding")
  /**
   * A signature by a signing subkey, indicating that it is owned by the
   * signed primary key.
   */
  val PRIMARY_KEY_BINDING = Value(PGPSignature.PRIMARYKEY_BINDING, "primary key binding")
  /**
   * A signature calculated directly on a key.
   *
   * It binds the information in the Signature subpackets to the key, and is
   * appropriate to be used for subpackets that provide information about the
   * key, such as the Revocation Key subpacket.  It is also appropriate for
   * statements that non-self certifiers want to make about the key itself,
   * rather than the binding between a key and a name.
   */
  val DIRECT_KEY = Value(PGPSignature.DIRECT_KEY, "direct key")
  /**
   * A signature calculated directly on the key being revoked.
   *
   * A revoked key is not to be used.  Only revocation signatures by the key
   * being revoked, or by an authorized revocation key, should be considered
   * valid revocation signatures.
   */
  val KEY_REVOCATION = Value(PGPSignature.KEY_REVOCATION, "key revocation")
  /**
   * A signature calculated directly on the subkey being revoked.
   *
   * A revoked subkey is not to be used.  Only revocation signatures by the
   * top-level signature key that is bound to this subkey, or by an authorized
   * revocation key, should be considered valid revocation signatures.
   */
  val SUBKEY_REVOCATION = Value(PGPSignature.SUBKEY_REVOCATION, "subkey revocation")
  /**
   * A signature revoking an earlier {@link #DEFAULT_CERTIFICATION},
   * {@link #NO_CERTIFICATION}, {@link #CASUAL_CERTIFICATION},
   * {@link #POSITIVE_CERTIFICATION} or {@link #DIRECT_KEY} signature.
   */
  val CERTIFICATION_REVOCATION = Value(PGPSignature.CERTIFICATION_REVOCATION, "certificate revocation")
  /**
   * A timestamp signature.
   *
   * This signature is only meaningful for the timestamp contained in it.
   */
  val TIMESTAMP = Value(PGPSignature.TIMESTAMP, "timestamp")
  /**
   * A signature over some other OpenPGP Signature packet(s).
   *
   * It is analogous to a notary seal on the signed data.
   */
  // this value isn't included as a constant in PGPSignature
  val THIRD_PARTY = Value(0x50, "third-party confirmation")
}

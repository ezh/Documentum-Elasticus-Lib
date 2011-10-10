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
import java.security.spec.DSAParameterSpec

import javax.crypto.spec.DHParameterSpec

/**
 * A pre-generated {@link DSAParameterSpec} for 1024-bit
 *
 * {@link AsymmetricAlgorithm#DSA} keys.
 * <p>
 * <b>N.B.:</b> {@code P}, {@code Q}, and {@code G} are all public values and
 * can be shared across networks of users.
 * @see <a href="http://en.wikipedia.org/wiki/Digital_Signature_Algorithm">Digital Signature Algorithm</a>
 */
private[this] object PQG_DSA_1024 {
  val P = BigInt(
    "pmqpa15uksb3tr1710v3m0ohs0i1utcoavzgk066lbp5rkvgjtjgqb0fj847osr54s23" +
      "w4g60p0a7v3yn0twefnvvqdqn29xpe9auvblylpirmeio1usdnxwdp9bcu9n1i9jtvty" +
      "glg49753mkd5wnyaztp3qo5sm6ussie7fsf2rss7jjbcj2trgnfq4sshdm6sp7",
    36)
  val Q = BigInt(
    "pcemwdiwzfzg7vw8n8el73hi1v3pelp",
    36)
  val G = BigInt(
    "42iz5oiscx7wtyascmwkesjh4socl98ex5y2kgmnl5xnc4ny2romijch7uk53qxtnq2k" +
      "grvbx4z5qclbkkz930by9iva1dk7o5s816nen7vdwtzo6bk7nnx40y2gu55wdyzirjct" +
      "5dzh0jqjjbl0vzqmzw2si1abrrrzfaskkpb7kyqne1qctmrt2j0ozls69boond",
    36)
}

object PregeneratedDSAParameterSpec extends DSAParameterSpec(PQG_DSA_1024.P.underlying, PQG_DSA_1024.Q.underlying, PQG_DSA_1024.G.underlying) {}

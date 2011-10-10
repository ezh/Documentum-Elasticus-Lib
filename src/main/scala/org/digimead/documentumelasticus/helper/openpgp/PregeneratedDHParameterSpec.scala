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

import javax.crypto.spec.DHParameterSpec

/**
 * A pre-generated {@link DHParameterSpec} for 2048-bit
 *
 * {@link AsymmetricAlgorithm#ELGAMAL} keys.
 * <p>
 * <b>N.B.:</b> {@code P} and {@code G} are both public values and can be shared
 * across networks of users.
 * @see <a href="http://en.wikipedia.org/wiki/ElGamal_encryption">ElGamal encryption</a>
 */
private[this] object P_n_G_DH {
  val P = BigInt(
    "1ikmyh3qcdgz825eegsk41g7msaustr13k16h06zxy6pwrh1bt2d7888nv77oybgmqok" +
      "8947twild1j14miwjfc9l0jr02a1dk6t1t5ynyeyh08dyisonl2fjlsp3eyz3936vtac" +
      "idp0pll9pr52crqoektouivzt4v3jk0jgp3dvux628zvrstd143zifw2dj3ed8kd4o37" +
      "0ze6qf53sx9nyv816kpihdw10723p7igep2fe5fe8fxpg8vqf4wyttnejwho4aa0eo15" +
      "q7noeeegck2h53q2o5e00myfdnn7y7dls52ixfr1wiyk2ovq1fg66jl382t0lb76usxj" +
      "5qifjs2hqioup6premvu6u1dwb8d0qucscfq3itqolmsdpkns5vu9rfsz",
    36)
  val G = BigInt(
    "v459uv2gxjbl1jqu7fhlvhe23oi1qtwqs6n8h635dkmc2o58kwa4jurbem9h9h87iq1k" +
      "6rqj5fxowbyvpeobz9k9ijcq03sue3o45506zmhw0husbxgwy8g14gzio6ct22k45zev" +
      "n6bwj7vpwq5eat72oervw0pccp9gg45qs9m6k4fn6vrp5avmmdbu91qlv075n4ojf8iv" +
      "9r7zc4mdvvb5akkwvl36hrqd3wei9e3p5ilk1z2vnenitzau40satbcx6eqfmivvsn7m" +
      "n8schdd4irr45yakbthfu3cw896r7ygx44r534sp7r5pkldeih6fp7cin6jysr4b7woe" +
      "aglyy167976n4eg1y99i1eb6561mg587hcf05j1woxzfi8m0565nvkpz",
    36)
}

object PregeneratedDHParameterSpec extends DHParameterSpec(P_n_G_DH.P.underlying, P_n_G_DH.G.underlying) {}

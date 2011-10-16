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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.{ KeyPairGenerator, SecureRandom, Security }
import java.util.Date
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.{ PGPKeyPair, PGPKeyRingGenerator }
import org.bouncycastle.openpgp.{ PGPSignatureSubpacketGenerator, PGPSignatureSubpacketVector }
import org.slf4j.LoggerFactory
import scala.actors.Futures.future
import org.bouncycastle.util.encoders.Base64

object OpenPGP {
  private val log = LoggerFactory.getLogger(getClass.getName)
  private val random = new SecureRandom
  private val encalg = SymmetricAlgorithm.DEFAULT.id
  private val compalg = CompressionAlgorithm.DEFAULT.id
  init()
  log.debug("alive")
  def init() {
    if (!Security.getProviders.exists(_.getName() == "BC version "))
      Security.addProvider(new BouncyCastleProvider())
  }
  def encrypt(keyset: KeySet[_], plain: Array[Byte]): Array[Byte] = encrypt(keyset.encriptionKey, plain)
  def encrypt(key: Key[_], plain: Array[Byte]): Array[Byte] = encrypt(Seq(key.publicKey), plain)
  def encrypt(publicKey: Seq[PGPPublicKey], plain: Array[Byte]): Array[Byte] = {
    val cpk = new PGPEncryptedDataGenerator(encalg, true, random, "BC")
    publicKey.foreach(cpk.addMethod(_))
    val buf = new ByteArrayOutputStream()
    val aout = new ArmoredOutputStream(buf)
    val cout = cpk.open(aout, plain.length)
    cout.write(plain)
    cout.close()
    aout.close()
    buf.toByteArray()
  }
  def compress(plain: Array[Byte], date: Date = PGPLiteralData.NOW): Array[Byte] = {
    val buf = new ByteArrayOutputStream()
    val comdg = new PGPCompressedDataGenerator(compalg)
    val len = plain.length
    val out = new PGPLiteralDataGenerator().open(comdg.open(buf), PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, len, date)
    out.write(plain)
    out.close()
    comdg.close()
    buf.toByteArray()
  }
  def encode64(plain: Array[Byte]): Option[String] = {
    import org.bouncycastle.util.encoders.Base64._
    try {
      Some(new String(encode(plain)))
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
  }
  def decode64(encoded: String): Option[Array[Byte]] = {
    import org.bouncycastle.util.encoders.Base64._
    try {
      Some(decode(encoded.getBytes))
    } catch {
      case e =>
        log.error(e.getMessage, e)
        None
    }
  }
  def decrypt(keyset: KeySet[Unlocked], encrypted: Array[Byte]): Option[Array[Byte]] = decrypt(keyset.encriptionKey, encrypted)
  def decrypt(key: Key[Unlocked], encrypted: Array[Byte]): Option[Array[Byte]] = decrypt(key.privateKey, encrypted)
  def decrypt(privateKey: PGPPrivateKey, encrypted: Array[Byte]): Option[Array[Byte]] = {
    decryptGetEncryptedData(privateKey, encrypted) match {
      case Some(data) =>
        val unencryptedStream = data.getDataStream(privateKey, "BC")
        Some(Stream.continually(unencryptedStream.read).takeWhile(_ != -1).map(_.toByte).toArray)
      case None =>
	None
    }
  }
  private def decryptGetEncryptedData(privateKey: PGPPrivateKey, encoded: Array[Byte]): Option[PGPPublicKeyEncryptedData] = {
    var encryptedData: Option[PGPPublicKeyEncryptedData] = None
    val factory = new PGPObjectFactory(PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded)))
    val encryptedDataList = factory.nextObject().asInstanceOf[PGPEncryptedDataList]
    if (encryptedDataList != null) {
      for (i <- 0 until encryptedDataList.size if (encryptedData == None)) {
        encryptedDataList.get(i) match {
          case data: PGPPublicKeyEncryptedData =>
            if (data.getKeyID() == privateKey.getKeyID()) {
              val symmetricAlgorithm = data.getSymmetricAlgorithm(privateKey, "BC")
              if (!SymmetricAlgorithm.ACCEPTABLE_ALGORITHMS.exists(_.id == symmetricAlgorithm)) {
                log.error("data is encrypted with " + SymmetricAlgorithm(symmetricAlgorithm) + " which is unacceptable")
              } else if (!data.isIntegrityProtected()) {
                log.error("missing integrity packet")
              } else {
                log.info("discower accepable encrypted data with " + SymmetricAlgorithm(symmetricAlgorithm))
                encryptedData = Some(data)
              }
            } else {
              log.debug("unknown key id " + data.getKeyID())
            }
          case block =>
            log.debug("unknown data block " + block)
        }
      }
    }
    encryptedData
  }
  def decompress() {
  }
  /**
   * Generates a new {@link KeySet}.
   *
   * @param userId the user ID, in {@code First Last <email@example.com>} format
   * @param passphrase the user's passphrase
   * @return a keyset for the user
   * @throws CryptographicException if there was an error generating the keyset
   */
  def generate(userId: String, passPhrase: String, random: SecureRandom = new SecureRandom): KeySet[Locked] = {
    log.debug("generate new keyset for " + userId)
    val signingKeyPair = future { generateKeyPair(AsymmetricAlgorithm.SIGNING_DEFAULT, random) }
    val encriptionKeyPair = future { generateKeyPair(AsymmetricAlgorithm.ENCRYPTION_DEFAULT, random) }
    val signingPGPKeyPair = new PGPKeyPair(AsymmetricAlgorithm.SIGNING_DEFAULT.id, signingKeyPair(), new Date())
    val generator = new PGPKeyRingGenerator(SignatureType.POSITIVE_CERTIFICATION.id, signingPGPKeyPair, userId,
      SymmetricAlgorithm.DEFAULT.id, passPhrase.toCharArray, true, // use SHA-1 instead of MD5
      generateMasterKeySettings(), null, // don't store any key settings unhashed
      random, "BC")
    val encriptionPGPKeyPair = new PGPKeyPair(AsymmetricAlgorithm.ENCRYPTION_DEFAULT.id, encriptionKeyPair(), new Date())
    generator.addSubKey(encriptionPGPKeyPair, generateSubKeySettings(), null)
    val keyRing = generator.generateSecretKeyRing()
    KeySet(keyRing)
  }
  private def generateMasterKeySettings(): PGPSignatureSubpacketVector = {
    val settings = new PGPSignatureSubpacketGenerator()
    settings.setKeyFlags(false, KeyFlag.MASTER_KEY_DEFAULTS.map(_.id).reduce(_ | _))
    settings.setPreferredSymmetricAlgorithms(false, SymmetricAlgorithm.ACCEPTABLE_ALGORITHMS.map(_.id).toArray)
    settings.setPreferredHashAlgorithms(false, HashAlgorithm.ACCEPTABLE_ALGORITHMS.map(_.id).toArray)
    settings.setPreferredCompressionAlgorithms(false, Seq(CompressionAlgorithm.BZIP2, CompressionAlgorithm.ZLIB, CompressionAlgorithm.ZIP).map(_.id).toArray)
    return settings.generate()
  }
  private def generateSubKeySettings(): PGPSignatureSubpacketVector = {
    val settings = new PGPSignatureSubpacketGenerator()
    settings.setKeyFlags(false, KeyFlag.SUB_KEY_DEFAULTS.map(_.id).reduce(_ | _))
    settings.generate()
  }
  private def generateKeyPair(algorithm: AsymmetricAlgorithm.Value, random: SecureRandom) = {
    val generator = KeyPairGenerator.getInstance(algorithm.toString(), "BC")
    generator.initialize(AsymmetricAlgorithm.getAlgorithmParameterSpec(algorithm), random)
    generator.generateKeyPair()
  }
}

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

package org.digimead.documentumelasticus.helper

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.UUID
import net.lag.configgy.Configgy
import net.lag.configgy.{ Config => CConfig }
import org.slf4j.LoggerFactory

class Config(val config: CConfig, private val file: File) {
  private val log = LoggerFactory.getLogger(getClass.getName)
  def uuid = config.getString("uuid") match {
    case Some(uuid) => UUID.fromString(uuid)
    case None =>
      log.warn("uuid not found, generate random uuid")
      val newUUID = UUID.randomUUID()
      config.setString("uuid", newUUID.toString())
      save()
      newUUID
  }
  def password = config.getString("password") match {
    case Some(password) => password
    case None =>
      log.warn("password not found, generate random password")
      val newPassword = ""
      config.setString("password", newPassword)
      save()
      newPassword
  }
  def save(cfgFile: File = file) {
    log.debug("save configuration to " + cfgFile)
    val out = new BufferedWriter(new FileWriter(cfgFile))
    out.write(config.toConfigString)
    out.close()
  }
}

object Config {
  implicit def Config2CConfig(from: Config): CConfig = from.config
  def load(file: File): Config = synchronized {
    Configgy.configure(file.getPath())
    new Config(Configgy.config, file)
  }
}

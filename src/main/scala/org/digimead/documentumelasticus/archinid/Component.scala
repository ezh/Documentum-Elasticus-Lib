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

package org.digimead.documentumelasticus.archinid

import java.io.{ BufferedInputStream, File, FileOutputStream }
import java.nio.channels.Channels
/*import org.apache.http
commons.httpclient.{DefaultHttpMethodRetryHandler, HttpClient, HttpStatus}
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.params.HttpMethodParams*/
import org.slf4j.LoggerFactory

class Component(val url: String,
  val homepage: String,
  val watchers: Int,
  val created_at: String,
  val pushed_at: String,
  val forks: Int,
  val name: String,
  val description: String,
  val open_issues: Int,
  val versions: Map[String, Component.Version]) {
  private val logger = LoggerFactory.getLogger(this.getClass)
  def download(path: File, version: String, force: Boolean = false): Boolean = {
    val timeout = 10
    /*    def httpGet(address: String, file: File): Boolean = {
      val httpClient = new HttpClient
      httpClient.getParams.setSoTimeout(timeout * 1000)
      httpClient.getHttpConnectionManager.getParams.setConnectionTimeout(timeout * 1000)
      httpClient.getParams.setParameter(HttpMethodParams.RETRY_HANDLER,
        new DefaultHttpMethodRetryHandler())
      val getMethod = new GetMethod(address)
      var remoteFileSize = 0L
      try {
        /*
         * connect
         */
        val localFileSize = file.length
        // server must support partial content for resume
        if (localFileSize > 0) {
          logger.debug("try to resume download from " + localFileSize)
          getMethod.addRequestHeader("Range", "bytes=" + localFileSize + "-")
        }
        getMethod.setFollowRedirects(true)
        val status = httpClient.executeMethod(getMethod)
        remoteFileSize = {
          val contentLengthHeader = getMethod.getResponseHeader("content-length")
          if (contentLengthHeader == null)
            return false
          contentLengthHeader.getValue.toLong
        }
        logger.info("local file size is " + localFileSize + " bytes")
        logger.info("remote file size is " + remoteFileSize + " bytes")
        logger.info("bytes remaining " + (remoteFileSize - localFileSize) + " bytes")
        if (localFileSize >= remoteFileSize) {
          logger.debug(path + " already downloaded")
          return true
        }
        /*
         * download
         */
        status match {
          case HttpStatus.SC_PARTIAL_CONTENT =>
            val fromByte = getMethod.getRequestHeader("Range").getValue.toLong
            logger.trace("resume download from " + fromByte)
            val fileChannel = new FileOutputStream(file).getChannel
            val inChannel = Channels.newChannel(new BufferedInputStream(getMethod.getResponseBodyAsStream))
            try {
              fileChannel.transferFrom(inChannel, fromByte, getMethod.getResponseContentLength)
            } catch {
              case e =>
                return false
            } finally {
              fileChannel.close
              inChannel.close
            }
          case HttpStatus.SC_OK =>
            logger.trace("download from the beginning")
            val fileChannel = new FileOutputStream(file).getChannel
            val inChannel = Channels.newChannel(new BufferedInputStream(getMethod.getResponseBodyAsStream))
            try {
              fileChannel.transferFrom(inChannel, 0, getMethod.getResponseContentLength)
            } catch {
              case e =>
                return false
            } finally {
              fileChannel.close
              inChannel.close
            }
          case _ =>
            logger.error("failed to get resource, code " + status)
            return false
        }
      } catch {
        case e =>
          return false
      } finally {
        getMethod.releaseConnection()
      }
      file.length == remoteFileSize
    }
    // check version
    if (version == "" || !versions.exists(_._1 == version)) {
      logger.error("version \"" + version + "\" for component \"" + name + "\" not found")
      return false
    }
    val componentLocalPath = new File(path.getAbsolutePath + File.separator +
      "components" + File.separator + name + File.separator +
      version + File.separator + ".keep")
    logger.info("download component \"" + name + "\" from \"" + url + "\" to \"" + componentLocalPath.getParent + "\"")
    componentLocalPath.getParentFile.mkdirs
    componentLocalPath.createNewFile
    versions(version).foreach(file => {
      val fileTo = new File(componentLocalPath.getParent + File.separator + file._1.split("/").mkString(File.separator))
      if (fileTo.exists && force == false) {
        logger.info("skip \"" + file._1 + "\" that already exists")
      } else {
        fileTo.getParentFile.mkdirs
        val retry = 5
        var successful = false
        for (i <- 0 to retry if !successful) {
          logger.info("download [" + i + "/" + retry + "] \"" + file._1 + "\"")
          if (httpGet(file._2, fileTo)) {
            successful = true
          } else {
            logger.error("download failed \"" + file._1 + "\"")
          }
        }
        if (!successful)
          return false
      }
    })*/
    true
  }
}

object Component {
  case class Version(val hash: String,
    val base: String,
    val file: Map[String, String])
}

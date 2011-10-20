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

package org.digimead.documentumelasticus.hexapod.archinid
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException

import java.io.{ BufferedInputStream, File, FileOutputStream }
import java.net.URL
import java.nio.channels.Channels
import java.security.KeyStore
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.params.ClientPNames
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.params.HttpConnectionParams
import org.digimead.documentumelasticus.helper.Version
import org.slf4j.LoggerFactory
import scala.collection.mutable.HashSet
import scala.io.BufferedSource
import scala.xml.XML

class Component(val url: String,
  val homepage: String,
  val watchers: Int,
  val created_at: String,
  val pushed_at: String,
  val forks: Int,
  val name: String,
  val description: String,
  val open_issues: Int,
  val versions: Map[String, Component.Version],
  val xmlFile: File) {
  private val log = LoggerFactory.getLogger(this.getClass)
  def latestVersion = versions.keys.map(new Version(_)).toList.sortWith(_ > _).head
  def check(localComponentsPath: File): Option[Boolean] = {
    log.debug("check " + name)
    // get all remote versions
    // get all local versions
    // if newest remote version < newest local version = Some(false)
    // if local empty = None
    // if the same = Some(true)
    Component.fromFile(xmlFile) match {
      case Some(lComponent) if (!lComponent.versions.isEmpty && !versions.isEmpty) =>
        val versionsDiff = new HashSet[String]()
        versions.keys.foreach(versionsDiff(_) = true)
        lComponent.versions.keys.foreach(v => if (versionsDiff(v)) versionsDiff.remove(v))
        versionsDiff.isEmpty match {
          case true =>
            Some(true)
          case false =>
            val sortedLocal = lComponent.versions.keys.map(new Version(_)).toList.sortWith(_ > _)
            val sortedRemote = versions.keys.map(new Version(_)).toList.sortWith(_ > _)
            Some(sortedLocal.head > sortedRemote.head)
        }
      case None if (!versions.isEmpty) =>
        None
      case None =>
        Some(true) // remote versions is empty
    }
  }
  def update(localComponentsPath: File, version: Option[String] = versions.keys.map(new Version(_)).toList.sortWith(_ > _).headOption.map(_.toString)): Boolean = {
    version match {
      case Some(version) =>
        log.debug("update " + name + " to version " + version)
        download(localComponentsPath, version)
      //        org.digimead.documentumelasticus.helper.Debug.debug[Map[String, Component.Version]]("v", versions)
      case None =>
        log.warn("update " + name + " failed: version unknown")
    }
    false
  }
  def download(localComponentsPath: File, version: String, force: Boolean = false): Boolean = {
    val timeoutSeconds = 10
    def httpGet(url: URL, file: File): Boolean = {
      val urlHost = url.getHost()
      val urlPort = if (url.getPort() == -1) url.getDefaultPort() else url.getPort()
      val urlProtocol = url.getProtocol()
      val urlStr = url.toString()
      val httpClient = new DefaultHttpClient
      val httpParams = httpClient.getParams
      // SSL
      // Set the hostname verifier:
      val hcVerifier = Component.verifier match {
        case 'STRICT => SSLSocketFactory.STRICT_HOSTNAME_VERIFIER
        case 'BROWSER_COMPATIBLE => SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER
        case 'ALLOW_ALL => SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        case _ => SSLSocketFactory.STRICT_HOSTNAME_VERIFIER
      }
      // Register the SSL Scheme:
      if (urlProtocol.equalsIgnoreCase("https")) {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        val instream = new FileInputStream(new File("my.keystore"))
        try {
          trustStore.load(instream, "nopassword".toCharArray());
        } catch {
          case e => log.warn(e.getMessage, e)
        } finally {
          try { instream.close() } catch { case _ => }
        }
        val socketFactory = new SSLSocketFactory(
          "TLS", // Algorithm
          null, // Keystore
          null, // Keystore password
          trustStore,
          null, // Secure Random
          hcVerifier)
        val sch = new Scheme(urlProtocol, urlPort, socketFactory)
        httpClient.getConnectionManager().getSchemeRegistry().register(sch)
      }
      // client params
      HttpConnectionParams.setConnectionTimeout(httpParams, timeoutSeconds * 1000)
      HttpConnectionParams.setSoTimeout(httpParams, timeoutSeconds * 1000)
      httpClient.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS, Component.isFollowRedirect)
      httpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler())
      val getMethod = new HttpGet(url.toURI())
      var remoteFileSize = 0L
      try {
        /*
         * connect
         */
        val localFileSize = file.length
        // server must support partial content for resume
        val expectedStatusCode = if (localFileSize > 0) {
          log.debug("try to resume download from " + localFileSize)
          getMethod.addHeader("Range", "bytes=" + localFileSize + "-")
          HttpStatus.SC_PARTIAL_CONTENT
        } else {
          HttpStatus.SC_OK
        }
        val response = httpClient.execute(getMethod)
        val statusCode = response.getStatusLine().getStatusCode()
        val bytesToSkip: Long = if (statusCode != expectedStatusCode) {
          if ((statusCode == HttpStatus.SC_OK)
            && (expectedStatusCode == HttpStatus.SC_PARTIAL_CONTENT)) {
            log.info("byte range request ignored")
            localFileSize
          } else {
            throw new IOException("Unexpected Http status code "
              + statusCode + " expected "
              + expectedStatusCode)
          }
        } else {
          0
        }
        remoteFileSize = {
          val contentLengthHeader = response.getFirstHeader("content-length")
          if (contentLengthHeader == null) {
            log.warn("'content-length' header not found")
            return false
          }
          contentLengthHeader.getValue.toLong
        }
        log.info("local file size is " + localFileSize + " bytes")
        log.info("remote file size is " + remoteFileSize + " bytes")
        log.info("bytes remaining " + (remoteFileSize - localFileSize) + " bytes")
        if (localFileSize >= remoteFileSize && file.exists()) {
          log.debug(file + " already downloaded")
          return true
        }
        /*
         * download
         */
        statusCode match {
          case HttpStatus.SC_PARTIAL_CONTENT =>
            val entity = response.getEntity()
            val fromByte = getMethod.getFirstHeader("Range").getValue.toLong
            log.trace("resume download from " + fromByte)
            val fileChannel = new FileOutputStream(file).getChannel
            val inChannel = Channels.newChannel(new BufferedInputStream(entity.getContent))
            try {
              fileChannel.transferFrom(inChannel, fromByte, entity.getContentLength)
              log.info("save " + file)
            } catch {
              case e =>
                return false
            } finally {
              fileChannel.close
              inChannel.close
            }
          case HttpStatus.SC_OK =>
            val entity = response.getEntity()
            val fileChannel = new FileOutputStream(file).getChannel
            val inChannel = Channels.newChannel(new BufferedInputStream(entity.getContent))
            try {
              fileChannel.transferFrom(inChannel, 0, entity.getContentLength)
            } catch {
              case e =>
                return false
            } finally {
              fileChannel.close
              inChannel.close
            }
          case _ =>
            log.error("failed to get resource, code " + statusCode)
            return false
        }
      } catch {
        case e =>
          return false
      }
      file.length == remoteFileSize
    }
    // check version
    if (!versions.isDefinedAt(version)) {
      log.error("version \"" + version + "\" for component \"" + name + "\" not found")
      return false
    }
    val localComponentPath = new File(new File(localComponentsPath, name), version)
    log.info("download component \"" + name + "\" from \"" + url + "\" to \"" + localComponentPath + "\"")
    localComponentPath.mkdirs
    val result = versions(version).file.par.forall(file => {
      val fileTo = new File(localComponentPath, file._1.split("/").mkString(File.separator))
      if (fileTo.exists && force == false) {
        log.info("skip \"" + file._1 + "\" that already exists")
        true
      } else {
        fileTo.getParentFile.mkdirs
        val retry = 5
        var successful = false
        for (i <- 0 to retry if !successful) {
          log.info("download [" + i + "/" + retry + "] \"" + file._1 + "\"")
          if (httpGet(new URL(file._2), fileTo)) {
            successful = true
          } else {
            log.error("download failed \"" + file._1 + "\"")
          }
        }
        successful
      }
    })
    if (result)
      toFile()
    result
  }
  def toXML(): scala.xml.Node =
    <component>
      <url>{ url }</url>
      <homepage>{ homepage }</homepage>
      <watchers>{ watchers }</watchers>
      <created_at>{ created_at }</created_at>
      <pushed_at>{ pushed_at }</pushed_at>
      <forks>{ forks }</forks>
      <name>{ name }</name>
      <description>{ description }</description>
      <open_issues>{ open_issues }</open_issues>
      <versionList>{
        for (v <- versions) yield {
          <version>
            <tag>{ v._1 }</tag>
            <hash>{ v._2.hash }</hash>
            <base>{ v._2.base }</base>
            <fileList>{
              v._2.file.map(f =>
                <file>
                  <local>{ f._1 }</local>
                  <remote>{ f._2 }</remote>
                </file>)
            }</fileList>
          </version>
        }
      }</versionList>
    </component>
  def toFile(file: File = xmlFile) = {
    log.debug("serialize component " + name + " to " + file)
    val out = new BufferedWriter(new FileWriter(file))
    out.write(toXML.toString)
    out.close()
  }
}

object Component {
  private val log = LoggerFactory.getLogger(this.getClass)
  val isFollowRedirect = true
  val verifier = 'ALLOW_ALL
  def localComponents(localComponentsPath: File): Seq[Component] = localComponentsPath.listFiles.filter(_.isDirectory).map(componentDir => {
    val xmlFile = new File(componentDir, ".component.xml")
    xmlFile.exists match {
      case true =>
        Component.fromFile(xmlFile)
      case false =>
        None
    }
  }).flatMap((a) => a)
  def fromFile(file: File): Option[Component] = {
    log.trace("load component from file " + file)
    try {
      fromXML(XML.loadFile(file), file.getParentFile.getParentFile)
    } catch {
      case e =>
        log.warn(e.getMessage(), e)
        None
    }
  }
  def fromXML(xml: scala.xml.Node, localComponentsPath: File): Option[Component] = {
    try {
      def parseVersion(xml: scala.xml.Node): (String, Component.Version) = {
        val files = Map[String, String]() ++ (for (file <- (xml \ "fileList" \ "file")) yield (file \ "local").text -> (file \ "remote").text)
        (xml \ "tag").text -> Component.Version(
	  (xml \ "tag").text,
          (xml \ "hash").text,
          (xml \ "base").text,
          files)
      }
      val versions = Map[String, Version]() ++ (for (version <- (xml \ "versionList" \ "version")) yield parseVersion(version))
      Some(new Component(
        (xml \ "url").text,
        (xml \ "homepage").text,
        (xml \ "watchers").text.toInt,
        (xml \ "created_at").text,
        (xml \ "pushed_at").text,
        (xml \ "forks").text.toInt,
        (xml \ "name").text,
        (xml \ "description").text,
        (xml \ "open_issues").text.toInt,
        versions,
        new File(new File(localComponentsPath, (xml \ "name").text), ".components.xml")))
    } catch {
      case e =>
        log.warn(e.getMessage(), e)
        None
    }
  }
  case class Version(val tag: String,
    val hash: String,
    val base: String,
    val file: Map[String, String])
}

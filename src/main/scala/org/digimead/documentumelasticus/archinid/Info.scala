/*
 *
 * This file is part of the Documentum Elasticus project.
 * Unpublished Copyright (c) 2010-2011 Limited Liability Company
 *     «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» CONFIDENTIAL
 * Author: Alexey Aksenov <ezh@ezh.msk.ru>
 * 
 * NOTICE:  All information contained herein is, and remains the property of
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS».
 * The intellectual and technical concepts contained herein are proprietary
 * to Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» and may be
 * covered by U.S. and Foreign Patents, patents in process, and are protected
 * by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is
 * strictly forbidden unless prior written permission is obtained from
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS». Access to
 * the source code contained herein is hereby forbidden to anyone except current
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure
 * agreements explicitly covering such access.
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes information that is
 * confidential and/or proprietary, and is a trade secret, 
 * of Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS».
 * 
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC PERFORMANCE, OR PUBLIC
 * DISPLAY OF OR THROUGH USE OF THIS SOURCE CODE WITHOUT THE EXPRESS WRITTEN
 * CONSENT OF COMPANY IS STRICTLY PROHIBITED, AND IN VIOLATION OF APPLICABLE
 * LAWS AND INTERNATIONAL TREATIES. THE RECEIPT OR POSSESSION OF THIS SOURCE CODE
 * AND/OR RELATED INFORMATION DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE,
 * DISCLOSE OR DISTRIBUTE ITS CONTENTS, OR TO MANUFACTURE, USE, OR SELL ANYTHING
 * THAT IT MAY DESCRIBE, IN WHOLE OR IN PART.
 *
 */

package org.digimead.documentumelasticus.archinid

import java.util.UUID

case class InfoIRC(
  nick: String,
  server: String,
  channels: Seq[String]
)

case class InfoJabber(
  nick: String,
  server: String
)

case class InfoAkka(
  ip: String
)

case class Info(
  uuid: UUID,
  ip: String,
  irc: Seq[InfoIRC],
  jabber: Seq[InfoJabber],
  akka: Seq[InfoAkka]
)

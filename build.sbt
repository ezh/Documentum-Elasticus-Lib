import java.util.jar._

name := "DocumentumElasticusLib"

organization := "org.digimead"

version := "0.0.1-SNAPSHOT"

libraryDependencies ++= {
  Seq(
    "log4j" % "log4j" % "1.2.16",
    "org.slf4j" % "slf4j-log4j12" % "1.6.1",
    "net.sf.ehcache" % "ehcache-core" % "2.4.1",
    "javax.transaction" % "jta" % "1.1",
    "pircbot" % "pircbot" % "1.5.0",
    "org.igniterealtime.smack" % "smack" % "3.2.1",
    "org.igniterealtime.smack" % "smackx" % "3.2.1",
    "org.apache.httpcomponents" % "httpclient" % "4.1.2",
    "org.bouncycastle" % "bcpg-jdk16" % "1.46"
//    "org.jasypt" % "jasypt" % "1.8"
  )
}
/*  val officeversion = "3.3"
  val basis = "file:///usr/lib/libreoffice/basis" + officeversion + "/program/classes/"
  val ure = "file:///usr/lib/libreoffice/ure/share/java"
    "oo" % "juh" % officeversion from "%s/juh.jar".format(ure),
    "oo" % "java_uno" % officeversion from "%s/java_uno.jar".format(ure),
    "oo" % "ridl" % officeversion from "%s/ridl.jar".format(ure),
    "oo" % "jurt" % officeversion from "%s/jurt.jar".format(ure),
    "oo" % "unoil" % officeversion from "%s/unoil.jar".format(basis),
*/
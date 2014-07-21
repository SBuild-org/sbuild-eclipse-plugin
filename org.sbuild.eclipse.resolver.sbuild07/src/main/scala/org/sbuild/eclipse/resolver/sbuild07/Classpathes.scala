package org.sbuild.eclipse.resolver.sbuild07

import java.io.BufferedInputStream
import java.util.Properties
import java.io.File
import java.io.FileInputStream

object Classpathes {

  def fromFile(propertiesFile: File): Classpathes = {
    val libDir = propertiesFile.getAbsoluteFile.getCanonicalFile.getParentFile

    val stream = new BufferedInputStream(new FileInputStream(propertiesFile))
    val props = new Properties()
    props.load(stream)

    fromProperties(libDir, props)
  }

  def fromProperties(sbuildLibDir: File, properties: Properties): Classpathes = {

    def splitAndPrepend(propertyValue: String): Array[String] = propertyValue.split(";|:").map {
      case lib if new File(lib).isAbsolute() => lib
      case lib => new File(sbuildLibDir.getAbsoluteFile, lib).getPath
    }

    Classpathes(
      splitAndPrepend(properties.getProperty("embeddedClasspath")))
  }

}

case class Classpathes(embeddedClasspath: Array[String])

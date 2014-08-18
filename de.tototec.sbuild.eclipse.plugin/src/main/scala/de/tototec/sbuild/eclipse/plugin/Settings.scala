package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore

import de.tototec.sbuild.eclipse.plugin.Logger.debug
import de.tototec.sbuild.eclipse.plugin.container.SBuildClasspathContainer

object Settings {
  val SBuildFileKey = "sbuildFile"
  val SBuildFileDefault = "SBuild.scala"

  val ExportedClasspathKey = "exportedClasspath"
  val ExportedClasspathDefault = "eclipse.classpath"

  val RelaxedFetchOfDependenciesKey = "relaxedFetchOfDependencies"
  val RelaxedFetchOfDependenciesDefault = true.toString

  val WorkspaceProjectAliasKey = "workspaceProjectAlias:"

  val ResolveSourcesKey = "resolveSources"
  val ResolveSourcesDefault = true.toString

  val ResolveJavadocKey = "resolveJavadoc"
  val ResolveJavadocDefault = false.toString
}

class Settings() {
  import Settings._
  
  def this(containerPath: IPath) = {
    this
    fromPath(containerPath)
  }

  private var options: Map[String, String] = Map()

  def toIClasspathEntry: IClasspathEntry = {
    JavaCore.newContainerEntry(new Path(
      SBuildClasspathContainer.ContainerName + "/" +
        options.map {
          case (k, v) => k + "=" + v
        }.mkString(",")
    ))
  }

  def fromPath(containerPath: IPath) {
    options = containerPath.segmentCount() match {
      case 0 | 1 => Map()
      case _ =>
        containerPath.lastSegment.split(",").map {
          _.split("=", 2) match {
            case Array(key, value) => (key, value)
            case Array(key) => (key, true.toString)
          }
        }.toMap
    }
    debug("Loaded from path: " + containerPath + " the options: " + options)
  }

  def fromIClasspathEntry(classpathEntry: IClasspathEntry) {
    classpathEntry match {
      case null => options = Map()
      case cpe => fromPath(classpathEntry.getPath)
    }
  }

  def sbuildFile: String =
    options.getOrElse(SBuildFileKey, SBuildFileDefault)
  def sbuildFile_=(sbuildFile: String) = sbuildFile match {
    case null => options -= SBuildFileKey
    case x if x.trim == "" => options -= SBuildFileKey
    case x if x == SBuildFileDefault => options -= SBuildFileKey
    case x => options += (SBuildFileKey -> x)
  }

  def exportedClasspath: String =
    options.getOrElse(ExportedClasspathKey, ExportedClasspathDefault)
  def exportedClasspath_=(exportedClasspath: String) = exportedClasspath match {
    case null => options -= ExportedClasspathKey
    case x if x.trim == "" => options -= ExportedClasspathKey
    case x if x == ExportedClasspathDefault => options -= ExportedClasspathKey
    case x => options += (ExportedClasspathKey -> x)
  }

  def relaxedFetchOfDependencies: Boolean =
    options.getOrElse(RelaxedFetchOfDependenciesKey, RelaxedFetchOfDependenciesDefault) == true.toString
  def relaxedFetchOfDependencies_=(relaxedFetchOfDependencies: Boolean) = relaxedFetchOfDependencies.toString match {
    case RelaxedFetchOfDependenciesDefault => options -= RelaxedFetchOfDependenciesKey
    case x => options += (RelaxedFetchOfDependenciesKey -> x)
  }

  def resolveSources: Boolean =
    options.getOrElse(ResolveSourcesKey, ResolveSourcesDefault) == true.toString
  def resolveSources_=(resolveSources: Boolean) = resolveSources.toString match {
    case ResolveSourcesDefault => options -= ResolveSourcesKey
    case x => options += (ResolveSourcesKey -> x)
  }

  def resolveJavadoc: Boolean =
    options.getOrElse(ResolveJavadocKey, ResolveJavadocDefault) == true.toString
  def resolveJavadoc_=(resolveJavadoc: Boolean) = resolveJavadoc.toString match {
    case ResolveJavadocDefault => options -= ResolveJavadocKey
    case x => options += (ResolveJavadocKey -> x)
  }

}
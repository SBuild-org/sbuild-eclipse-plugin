package de.tototec.sbuild.eclipse.plugin

import java.io.File
import java.net.URLClassLoader
import scala.util.Try
import org.eclipse.core.runtime.IProgressMonitor
import scala.util.Success
import java.lang.reflect.Constructor
import de.tototec.sbuild.embedded.SBuildEmbedded
import de.tototec.sbuild.SBuildException
import scala.util.Failure
import java.util.Properties
import java.lang.reflect.Method
import de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator

class SBuildResolver(sbuildHomeDir: File, projectFile: File) {

  private val classloader = SBuildClasspathActivator.activator.sbuildEmbeddedClassLoader(sbuildHomeDir)

  private[this] val (
    sbuildEmbeddedClassCtr: Constructor[_],
    sbuildExceptionClass: Class[_],
    getEmbeddedResolverMethod: Method,
    getExportedDependenciesMethod: Method,
    nullProgressMonitorClass: Class[_],
    resolveMethod: Method
    ) =
    {
      debug("Trying to use SBuild installed at: " + sbuildHomeDir)

      val sbuildVersionClass = classloader.loadClass("de.tototec.sbuild.SBuildVersion")
      val versionMethod = sbuildVersionClass.getMethod("version")
      debug("SBuild version: " + versionMethod.invoke(null))

      val sbuildExceptionClass = classloader.loadClass("de.tototec.sbuild.SBuildException")

      val sbuildEmbeddedClass = classloader.loadClass("de.tototec.sbuild.embedded.SBuildEmbedded")
      val progressMonitorClass = classloader.loadClass("de.tototec.sbuild.embedded.ProgressMonitor")
      val nullProgressMonitorClass = classloader.loadClass("de.tototec.sbuild.embedded.NullProgressMonitor")
      val embeddedResolverClass = classloader.loadClass("de.tototec.sbuild.embedded.EmbeddedResolver")

      val sbuildEmbeddedClassCtr = sbuildEmbeddedClass.getConstructor(classOf[File])

      val getEmbeddedResolverMethod = sbuildEmbeddedClass.getMethod("loadResolver", classOf[File], classOf[Properties])

      val getExportedDependenciesMethod = embeddedResolverClass.getMethod("exportedDependencies", classOf[String])

      val resolveMethod = embeddedResolverClass.getMethod("resolve", classOf[String], progressMonitorClass)

      (
        sbuildEmbeddedClassCtr,
        sbuildExceptionClass,
        getEmbeddedResolverMethod,
        getExportedDependenciesMethod,
        nullProgressMonitorClass,
        resolveMethod
      )
    }

  protected val sbuildEmbededed: Any = sbuildEmbeddedClassCtr.newInstance(sbuildHomeDir)

  private[this] var _resolver: Option[Any] = None
  protected def resolver: Any = _resolver.getOrElse {
    val newResolver = getEmbeddedResolverMethod.invoke(sbuildEmbededed, projectFile, new Properties())
    _resolver = Some(newResolver)
    newResolver
  }

  def exportedDependencies(exportName: String): Seq[String] = try {
    getExportedDependenciesMethod.
      invoke(resolver, exportName).
      asInstanceOf[Seq[String]]
  } catch {
    case e: Throwable if sbuildExceptionClass.isInstance(e) =>
      debug(s"""Could not retrieve exported dependencies "${exportName}".""", e)
      Seq()
  }

  /**
   * Resolve the given dependency.
   */
  def resolve(dep: String, progressMonitor: IProgressMonitor): Try[Seq[File]] = try {
    resolveMethod.
      invoke(resolver, dep, nullProgressMonitorClass.newInstance.asInstanceOf[Object]).
      asInstanceOf[Try[Seq[File]]]
  } catch {
    case e: Throwable =>
      debug(s"""Could not resolve depenendce "${dep}".""", e)
      Failure(e)
  }

}
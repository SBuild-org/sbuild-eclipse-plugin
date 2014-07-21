package org.sbuild.eclipse.resolver.sbuild07

import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.ArrayList
import java.util.{List => JList}
import java.util.Properties

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import org.sbuild.eclipse.resolver.{Either => JEither}
import org.sbuild.eclipse.resolver.SBuildResolver

class SBuild07Resolver(sbuildHomeDir: File) extends SBuildResolver {

  case class State(
    sbuildEmbeddedClassCtr: Constructor[_],
    sbuildExceptionClass: Class[_],
    getEmbeddedResolverMethod: Method,
    getExportedDependenciesMethod: Method,
    nullProgressMonitorClass: Class[_],
    resolveMethod: Method,
    sbuildEmbedded: Any)

  private[this] def debug(msg: => String): Unit = {}

  private[this] val state = {
    val embeddedClasspath = Classpathes.fromFile(new File(sbuildHomeDir, "lib/classpath.properties")).embeddedClasspath
    val classloader = new URLClassLoader(embeddedClasspath.map { path => new File(path).toURI.toURL }, getClass.getClassLoader)

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

    val sbuildEmbedded: Any = sbuildEmbeddedClassCtr.newInstance(sbuildHomeDir)

    State(
      sbuildEmbeddedClassCtr,
      sbuildExceptionClass,
      getEmbeddedResolverMethod,
      getExportedDependenciesMethod,
      nullProgressMonitorClass,
      resolveMethod,
      sbuildEmbedded)
  }

  import state._

  case class Cached(timestamp: Long, ok: Boolean, projectFile: File, resolver: Any)

  private[this] var cache: Map[File, Cached] = Map()
  protected def resolverForProject(projectFile: File): Any = {
    cache.get(projectFile) match {
      case Some(Cached(timestamp, ok, file, resolver)) if ok == true => resolver
      case None =>
        val newResolver = getEmbeddedResolverMethod.invoke(state.sbuildEmbedded, projectFile, new Properties())
        cache += projectFile -> Cached(System.currentTimeMillis(), true, projectFile, newResolver)
        newResolver
    }
  }

  override def exportedDependencies(projectFile: File, exportName: String): JEither[Throwable, JList[String]] =
    try {
      JEither.right(
        new ArrayList(getExportedDependenciesMethod.invoke(resolverForProject(projectFile), exportName).
          asInstanceOf[Seq[String]].asJavaCollection))
    } catch {
      case e: InvocationTargetException if sbuildExceptionClass.isInstance(e.getCause()) =>
        debug(s"""Could not retrieve exported dependencies "${exportName}". Casue: ${e}""")
        JEither.left(e.getCause())
      case NonFatal(e) => JEither.left(e)
    }

  override def resolve(projectFile: File, dependency: String): JEither[Throwable, JList[File]] = try {
    resolveMethod.
      invoke(resolverForProject(projectFile), dependency, nullProgressMonitorClass.newInstance.asInstanceOf[Object]).
      asInstanceOf[Try[Seq[File]]] match {
        case Success(s) => JEither.right(new ArrayList(s.asJavaCollection))
        case Failure(e) => JEither.left(e)
      }
  } catch {
    case NonFatal(e) =>
      debug(s"""Could not resolve depenendce "${dependency}". Cause: ${e}""")
      JEither.left(e)
  }
}
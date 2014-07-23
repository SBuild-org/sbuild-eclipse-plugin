package de.tototec.sbuild.eclipse.plugin

import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.Properties

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

class SBuildResolver(sbuildHomeDir: File) {

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

  case class Cached(timestamp: Long, projectFile: File, resolver: Try[Any])

  // TODO: limit cache size
  private[this] var cache: Map[File, Cached] = Map()

  protected def resolverForProject(projectFile: File): Try[Any] = {
    cache.get(projectFile) match {
      case Some(Cached(timestamp, file, resolver)) => resolver
      case _ =>
        Try(getEmbeddedResolverMethod.invoke(state.sbuildEmbedded, projectFile, new Properties()))
    }
  }

  def prepareProject(projectFile: File, keepFailed: Boolean): Option[Throwable] = {
    cache.get(projectFile) match {
      case Some(_) => None
      case None =>
        val resolver = resolverForProject(projectFile)
        if (resolver.isSuccess || keepFailed)
          debug(s"Caching resolver for: ${projectFile} -> ${resolver}")
        synchronized {
          cache += projectFile -> Cached(System.currentTimeMillis(), projectFile, resolver)
        }
        resolver match {
          case Success(_) => None
          case Failure(e) => Some(e)
        }
    }
  }

  def releaseProject(projectFile: File): Unit = {
    cache.get(projectFile).foreach { _ =>
      debug(s"Evict resolver for: ${projectFile}")
      synchronized {
        cache -= projectFile
      }
    }
  }

  private[this] def withResolver[T](projectFile: File)(f: Any => Either[Throwable, T]): Either[Throwable, T] = resolverForProject(projectFile) match {
    case Success(resolver) => f(resolver)
    case Failure(e) => Left(e)
  }

  def exportedDependencies(projectFile: File, exportName: String): Either[Throwable, Array[String]] = withResolver(projectFile) { resolver =>
    try {
      Right(
        getExportedDependenciesMethod.invoke(resolver, exportName).
          asInstanceOf[Seq[String]].toArray)
    } catch {
      case e: InvocationTargetException if sbuildExceptionClass.isInstance(e.getCause()) =>
        debug(s"""Could not retrieve exported dependencies "${exportName}". Casue: ${e}""")
        Left(e.getCause())
      case NonFatal(e) => Left(e)
    }
  }

  def resolve(projectFile: File, dependency: String): Either[Throwable, Array[File]] = withResolver(projectFile) { resolver =>
    try {
      resolveMethod.
        invoke(resolver, dependency, nullProgressMonitorClass.newInstance.asInstanceOf[Object]).
        asInstanceOf[Try[Seq[File]]] match {
          case Success(s) => Right(s.toArray)
          case Failure(e) => Left(e)
        }
    } catch {
      case NonFatal(e) =>
        debug(s"""Could not resolve depenendce "${dependency}". Cause: ${e}""")
        Left(e)
    }
  }
}

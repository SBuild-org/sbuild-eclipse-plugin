package org.sbuild.eclipse.resolver.sbuild08

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
import org.sbuild.eclipse.resolver.SBuildResolver
import org.sbuild.eclipse.resolver.{ Either => JEither }
import org.sbuild.eclipse.resolver.Optional

class SBuild08Resolver(sbuildHomeDir: File) extends SBuildResolver {

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

    val sbuildVersionClass = classloader.loadClass("org.sbuild.SBuildVersion")
    val versionMethod = sbuildVersionClass.getMethod("version")
    debug("SBuild version: " + versionMethod.invoke(null))

    val sbuildExceptionClass = classloader.loadClass("org.sbuild.SBuildException")

    val sbuildEmbeddedClass = classloader.loadClass("org.sbuild.embedded.SBuildEmbedded")
    val progressMonitorClass = classloader.loadClass("org.sbuild.embedded.ProgressMonitor")
    val nullProgressMonitorClass = classloader.loadClass("org.sbuild.embedded.NullProgressMonitor")
    val embeddedResolverClass = classloader.loadClass("org.sbuild.embedded.EmbeddedResolver")

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
        Try(getEmbeddedResolverMethod.invoke(state.sbuildEmbedded, projectFile, new Properties())) recoverWith {
          case e: InvocationTargetException if sbuildExceptionClass.isInstance(e.getCause()) =>
            Failure(e.getCause())
        }
    }
  }

  def prepareProject(projectFile: File, keepFailed: Boolean): Optional[Throwable] = {
    cache.get(projectFile) match {
      case Some(_) => Optional.none()
      case None =>
        val resolver = resolverForProject(projectFile)
        if (resolver.isSuccess || keepFailed)
          debug(s"Caching resolver for: ${projectFile} -> ${resolver}")
        synchronized {
          cache += projectFile -> Cached(System.currentTimeMillis(), projectFile, resolver)
        }
        resolver match {
          case Success(_) => Optional.none()
          case Failure(e) => Optional.some(e)
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

  private[this] def withResolver[T](projectFile: File)(f: Any => Either[Throwable, T]): JEither[Throwable, T] = resolverForProject(projectFile) match {
    case Success(resolver) => f(resolver) match {
      case Left(e) => JEither.left(e)
      case Right(r) => JEither.right(r)
    }
    case Failure(e) => JEither.left(e)
  }

  override def exportedDependencies(projectFile: File, exportName: String): JEither[Throwable, Array[String]] = withResolver(projectFile) { resolver =>
    try {
      Right(
        getExportedDependenciesMethod.invoke(resolver, exportName).
          asInstanceOf[Seq[String]].toArray)
    } catch {
      case e: InvocationTargetException if sbuildExceptionClass.isInstance(e.getCause()) =>
        //        debug(s"""Could not retrieve exported dependencies "${exportName}". Casue: ${e}""")
        Left(e.getCause())
      case NonFatal(e) => Left(e)
    }
  }

  override def resolve(projectFile: File, dependency: String): JEither[Throwable, Array[File]] = withResolver(projectFile) { resolver =>
    try {
      resolveMethod.
        invoke(resolver, dependency, nullProgressMonitorClass.newInstance.asInstanceOf[Object]).
        asInstanceOf[Try[Seq[File]]] match {
          case Success(s) => Right(s.toArray)
          case Failure(e) => Left(e)
        }
    } catch {
      case e: InvocationTargetException if sbuildExceptionClass.isInstance(e.getCause()) =>
        //        debug(s"Could not resolve dependency: ${dependency}", e)
        Left(e.getCause())
      case NonFatal(e) =>
        //        debug(s"Could not resolve dependency: ${dependency}", e)
        Left(e)
    }
  }
}
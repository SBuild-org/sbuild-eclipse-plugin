package de.tototec.sbuild.eclipse.plugin

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLClassLoader
import java.util.zip.ZipFile
import scala.collection.JavaConversions._
import scala.xml.XML
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.SBuildVersion
import de.tototec.sbuild.Project
import de.tototec.sbuild.runner.Config
import de.tototec.sbuild.runner.SBuildRunner
import de.tototec.sbuild.runner.SimpleProjectReader
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore
import de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator
import java.net.URL
import de.tototec.sbuild.runner.ClasspathConfig
import java.util.zip.ZipInputStream
import java.io.FileInputStream
import java.lang.reflect.Method
import java.lang.reflect.Constructor
import de.tototec.sbuild.ExportDependencies

trait SBuildClasspathProjectReader {
  def buildFile: File
  def readResolveActions: Seq[ResolveAction]
}

object SBuildClasspathProjectReader {

  /**
   * Load the SBuild installation (assumes, it is compiled against a binary compatible Scala version.
   *
   * TODO: First evaluate the required SBuild version of the project.
   */
  def load(sbuildHomeDir: File, settings: Settings, projectRootFile: File): SBuildClasspathProjectReader = {

    val classpathes = Classpathes.fromFile(new File(sbuildHomeDir, "lib/classpath.properties"))

    val projectFile = new File(projectRootFile, settings.sbuildFile)

    new EmbeddedSBuildClasspathProjectReader(classpathes, projectFile, settings.exportedClasspath)
  }
}

class EmbeddedSBuildClasspathProjectReader(
  classpathes: Classpathes,
  override val buildFile: File,
  exportedClasspath: String)
    extends SBuildClasspathProjectReader {

  protected lazy val (
    sbuildHomeDir: File,
    sbuildEmbeddedClassCtr: Constructor[_],
    exportedDependenciesMethod: Method,
    resolveMethod: Method,
    dependenciesMethod: Method,
    fileDependenciesMethod: Method,
    depToFileMapMethod: Method
    ) = {

    // Create an classloader that contains the SBuild libraries and the SBuildClasspathProjectReader implementation. 
    // The parent classloader is used to load all other dependencies, e.g. Scala runtime. 
    val sbuildClassloader = new URLClassLoader(
      classpathes.embeddedClasspath.map { path =>
        new File(path).toURI.toURL
      },
      getClass.getClassLoader
    ) {
      override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
        try {
          super.loadClass(name, resolve)
        } catch {
          case e: ClassNotFoundException =>
            // TODO: If the class is e.g. SBuild$$annonfun..., than, the reason might be, 
            // that someone cleaned and/or recompiled the build script outside eclipse.
            // we might be able to recover from this, by throwing a special marker exception. 
            // The ClasspathContainer could handle it by re-creating the SBuild project classpath container loader.
            error("Could not found required class: " + name, e)
            throw e
        }
      }
    }

    val sbuildEmbeddedClass = sbuildClassloader.loadClass("de.tototec.sbuild.embedded.SBuildEmbedded");
    val sbuildEmbeddedClassCtr = sbuildEmbeddedClass.getConstructor(classOf[File], classOf[File])

    // val projectFile = new File(projectRootFile, settings.sbuildFile)

    val sbuildHomePath: IPath = JavaCore.getClasspathVariable(SBuildClasspathContainer.SBuildHomeVariableName)
    if (sbuildHomePath == null) {
      error("Classpath variable 'SBUILD_HOME' not defined")
      throw new RuntimeException("Classpath variable 'SBUILD_HOME' not defined")
    }
    val sbuildHomeDir = sbuildHomePath.toFile
    //    debug("Trying to use SBuild " + SBuildVersion.version + " installed at: " + sbuildHomeDir)
    debug("Trying to use SBuild installed at: " + sbuildHomeDir)

    val sbuildVersionClass = sbuildClassloader.loadClass("de.tototec.sbuild.SBuildVersion")
    val versionMethod = sbuildVersionClass.getMethod("version")
    debug("SBuild version: " + versionMethod.invoke(null))

    val exportedDependenciesResolverClass = sbuildClassloader.loadClass("de.tototec.sbuild.embedded.ExportedDependenciesResolver")
    val exportedDependenciesMethod = sbuildEmbeddedClass.getMethod("exportedDependencies", classOf[String])

    val dependenciesMethod = exportedDependenciesResolverClass.getMethod("dependencies")

    val fileDependenciesMethod = exportedDependenciesResolverClass.getMethod("fileDependencies")

    val depToFileMapMethod = exportedDependenciesResolverClass.getMethod("depToFileMap")

    val resolveMethod = exportedDependenciesResolverClass.getMethod("resolve", classOf[File])

    (sbuildHomeDir, sbuildEmbeddedClassCtr, exportedDependenciesMethod, resolveMethod, dependenciesMethod, fileDependenciesMethod, depToFileMapMethod)
  }

  override def readResolveActions: Seq[ResolveAction] = {

    val sbuildEmbedded = sbuildEmbeddedClassCtr.newInstance(buildFile, sbuildHomeDir)
    val exportedDependenciesResolver = exportedDependenciesMethod.invoke(sbuildEmbedded, exportedClasspath)
    val deps: Seq[String] = dependenciesMethod.invoke(exportedDependenciesResolver).asInstanceOf[Seq[String]]
    val fileDeps: Seq[File] = fileDependenciesMethod.invoke(exportedDependenciesResolver).asInstanceOf[Seq[File]]
    val depToFileMap: Map[String, Seq[File]] = depToFileMapMethod.invoke(exportedDependenciesResolver).asInstanceOf[Map[String, Seq[File]]]

    var resolveActions = Seq[ResolveAction]()

    depToFileMap.foreach {
      case (dep, files) =>
        files.toList match {
          case Nil =>
            // ignore phony target
            debug(s"""Ignoring phony dependency "${dep}"""")
          case file :: tail =>
            // only take first file
            def action: Boolean = {
              val resolved: Either[String, File] = resolveMethod.invoke(exportedDependenciesResolver, file).asInstanceOf[Either[String, File]]
              resolved match {
                case Right(file) =>
                  debug(s"""Resolved dependency "${dep}" to file: """ + file)
                  true
                case Left(msg) =>
                  error(s"""Could not resolve dependency "${dep}". Reason: """ + msg)
                  false
              }
            }
            resolveActions ++= Seq(ResolveAction(file.getAbsolutePath, dep, action _))

            if (!tail.isEmpty) debug(s"""Dependency "${dep}" produced more than one file. Only the first one is currently used.""")
        }
    }

    resolveActions
  }
}


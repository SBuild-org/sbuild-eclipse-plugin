package de.tototec.sbuild.eclipse.plugin

import java.io.File
import scala.collection.JavaConversions._
import scala.xml.XML
import scala.xml.factory.XMLLoader
import org.eclipse.core.resources.ProjectScope
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IJavaModel
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.JavaModelException
import scala.util.Failure
import scala.util.Success
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor

object SBuildClasspathContainer {
  val ContainerName = "de.tototec.sbuild.SBUILD_DEPENDENCIES"
  def SBuildHomeVariableName = "SBUILD_HOME"

  def getSBuildClasspathContainers(javaProjects: Array[IJavaProject]): Array[SBuildClasspathContainer] =
    javaProjects.flatMap { p => getSBuildClasspathContainers(p) }

  def getSBuildClasspathContainers(javaProject: IJavaProject): Array[SBuildClasspathContainer] = {
    if (javaProject == null || !javaProject.exists()) {
      Array()
    } else try {
      val sbuildContainerEntries = javaProject.getRawClasspath.filter { entry =>
        entry != null && entry.getEntryKind == IClasspathEntry.CPE_CONTAINER && entry.getPath.segment(0) == ContainerName
      }
      val sbuildContainers = sbuildContainerEntries.map { entry => JavaCore.getClasspathContainer(entry.getPath, javaProject) }
      sbuildContainers.partition(_.isInstanceOf[SBuildClasspathContainer])._1.map { _.asInstanceOf[SBuildClasspathContainer] }
    } catch {
      case e: Exception =>
        debug("Caught exception while retrieving SBuild containers.", e)
        Array()
    }
  }
}

/**
 * A ClasspathContainer, that will read a SBuild build file and read a special marked classpath available in Eclipse.
 *
 * TODO: check of file changes of buildfile
 * TODO: resolve the classpath in background
 *
 */
class SBuildClasspathContainer(path: IPath, val project: IJavaProject) extends IClasspathContainer {
  debug("Creating new classpath container instance: " + this)

  override val getKind = IClasspathContainer.K_APPLICATION
  override val getDescription = "SBuild Libraries"
  override val getPath = path

  protected def projectRootFile: File = project.getProject.getLocation.makeAbsolute.toFile

  def this(predecessor: SBuildClasspathContainer) {
    this(predecessor.getPath, predecessor.project)
  }

  protected var classpathEntries: Option[Array[IClasspathEntry]] = None
  protected var relatedWorkspaceProjectNames: Set[String] = Set()

  def dependsOnWorkspaceProjects(projectNames: Array[String]) =
    relatedWorkspaceProjectNames.exists(name => projectNames.contains(name))

  protected val settings: Settings = new Settings(path)

  override def getClasspathEntries: Array[IClasspathEntry] =
    classpathEntries match {
      case Some(x) => x
      case None => try {
        // first run, no background etc.
        debug("Evaluating classpath for the first time for project: " + project.getProject().getName())
        val (newClasspathEntries, relatedWorkspaceProjectNames) = calcClasspath
        val classpathEntriesArray = newClasspathEntries.toArray
        this.classpathEntries = Some(classpathEntriesArray)
        this.relatedWorkspaceProjectNames = relatedWorkspaceProjectNames
        classpathEntriesArray
      } catch {
        case e: Throwable =>
          error("Could not calculate classpath entries for project " + project.getProject().getName(), e)
          Array()
      }
    }

  /**
   * @return A tuple of 1. The classpath entries to use, 2. All potentially related Workspace projects
   */
  def calcClasspath: (Seq[IClasspathEntry], Set[String]) = {
    // TODO: do the heavy work in background (or Job)

    // read the project

    val sbuildHomePath: IPath = JavaCore.getClasspathVariable(SBuildClasspathContainer.SBuildHomeVariableName)
    if (sbuildHomePath == null) {
      throw new RuntimeException("Classpath variable 'SBUILD_HOME' not defined.")
    }
    val sbuildHomeDir = sbuildHomePath.toFile
    val projectFile = new File(projectRootFile, settings.sbuildFile).getAbsoluteFile

    info("Reading project: " + project.getProject.getName)
    val resolver = new SBuildResolver(sbuildHomeDir, projectFile)

    //    val reader = SBuildClasspathProjectReader.load(sbuildHomeDir, settings, projectRootFile)

    // TODO: Old up-to-date check logic was incomplete. Removed it completely for now

    debug("Reading project and resolve action definitions.")

    val deps = resolver.exportedDependencies(settings.exportedClasspath)
    debug("Exported dependencies: " + deps.mkString(","))

    val javaModel: IJavaModel = JavaCore.create(project.getProject.getWorkspace.getRoot)

    val aliases = WorkspaceProjectAliases(project)
    debug("Using workspaceProjectAliases: " + aliases)

    /** Resolve through embedded SBuild. Slurping all SBuild errors, but logging them. */
    def resolveViaSBuild(dep: String): Seq[IClasspathEntry] =
      resolver.resolve(dep, new NullProgressMonitor()) match {
        case Failure(e) =>
          debug(s"""Could not resolve dependency "${dep}" of project: ${project.getProject.getName}.""", e)
          Seq()
        case Success(files) =>
          debug(s"""Resolved dependency "${dep}" to "${files.mkString(", ")}" for project ${project.getProject.getName}.""")
          
          var singleSource: Option[File] = None
          var singleJavadoc: Option[File] = None
          
          if (settings.resolveSources) {
            if (files.size == 1) {
              // resolve sources via "source" scheme handler
              resolver.resolve("source:" + dep, new NullProgressMonitor()) match {
                case Success(sources) =>
                  debug(s"""Successfully resolved sources for dep "${dep}"""")
                  if (sources.size == 1) {
                    debug(s"""Attaching sources for dep "${dep}": ${sources.mkString(", ")}""")
                    singleSource = Some(sources.head)
                  } else debug(s"""Avoit attaching of resolved sources for dep "${dep}" as they are not exactly one file.""")
                case Failure(e) =>
                  debug(s"""Could not resolve sources for dep "${dep}" of project: ${project.getProject.getName}.""", e)
              }
            } else debug(s"""Skip resolve of sources for "${dep}" as they does not resolved to exactly one file.""")
          }
          
          if (settings.resolveJavadoc) {
            if (files.size == 1) {
              // resolve javadoc via "javadoc" scheme handler
              resolver.resolve("javadoc:" + dep, new NullProgressMonitor()) match {
                case Success(javadoc) =>
                  debug(s"""Successfully resolved javadoc for dep "${dep}"""")
                  if (javadoc.size == 1) {
                    debug(s"""Attaching javadoc for dep "${dep}": ${javadoc.mkString(", ")}""")
                    singleSource = Some(javadoc.head)
                  } else debug(s"""Avoit attaching of resolved javadoc for dep "${dep}" as they are not exactly one file.""")
                case Failure(e) =>
                  debug(s"""Could not resolve javadoc for dep "${dep}" of project: ${project.getProject.getName}.""", e)
              }
            } else debug(s"""Skip resolve of javadoc for "${dep}" as they does not resolved to exactly one file.""")
          }
          
          files.map { file =>
            // IDEA: refresh the resource
            //            project.getProject().getWorkspace().getRoot().findFilesForLocationURI(file.toURI()).foreach {
            //              iFile => iFile.refreshLocal(IResource.DEPTH_INFINITE, null)
            //            }
            val sourcePath = singleSource match {
              case Some(file) => new Path(file.getAbsolutePath())
              case _ => null
            }
            val javadocPath = singleJavadoc match {
              case Some(file) => new Path(file.getAbsolutePath())
              case _ => null
            }

            JavaCore.newLibraryEntry(new Path(file.getAbsolutePath()), sourcePath, javadocPath)
          }
      }

    var relatedWorkspaceProjectNames: Set[String] = Set()

    // We will now check, if some of these targets can be resolved from workspace.
    // - If so, we instead add an classpath entry with the existing and open workspace project
    // - Else, we resolve the depenency and add the result to the classpath
    val classpathEntries =
      deps.flatMap { dep =>
        aliases.getAliasForDependency(dep) match {
          case None => resolveViaSBuild(dep)
          case Some(alias) =>
            relatedWorkspaceProjectNames += alias
            javaModel.getJavaProject(alias) match {
              case javaProject if javaProject.exists && javaProject.getProject.isOpen =>
                debug("Using Workspace Project '" + javaProject.getProject.getName + "' as alias for project: " + dep)
                Seq(JavaCore.newProjectEntry(javaProject.getPath))
              case _ => resolveViaSBuild(dep)
            }
        }
      }

    (classpathEntries.distinct, relatedWorkspaceProjectNames)
  }

  def updateClasspath(monitor: IProgressMonitor): Unit = try {
    //    classpathEntries = None
    val newContainer = new SBuildClasspathContainer(this)
    monitor.subTask("Updating SBuild library container")
    debug("Updating classpath (by re-setting classpath container)")
    JavaCore.setClasspathContainer(path, Array(project), Array(newContainer), monitor)
  } finally {
    monitor.done()
  }

}

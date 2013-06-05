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
import scala.util.Try
import org.eclipse.core.resources.IMarker
import scala.collection.JavaConverters._
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IFile

object SBuildClasspathContainer {
  val ContainerName = "de.tototec.sbuild.SBUILD_DEPENDENCIES"
  def SBuildHomeVariableName = "SBUILD_HOME"
  def ContainerDescription = "SBuild Libraries"

  def getSBuildClasspathContainerForResource(resource: IResource): Option[SBuildClasspathContainer] = {
    val javaProj = JavaCore.create(resource.getProject())
    getSBuildClasspathContainers(javaProj) match {
      case Array() => None
      case containers => Some(containers.head)
    }
  }

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
      sbuildContainers.collect { case s: SBuildClasspathContainer => s }
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
  import SBuildClasspathContainer._

  debug("Creating new classpath container instance: " + this)

  override val getKind = IClasspathContainer.K_APPLICATION
  override val getDescription = ContainerDescription
  override val getPath = path

  protected def projectRootFile: File = project.getProject.getLocation.makeAbsolute.toFile

  def this(predecessor: SBuildClasspathContainer) {
    this(predecessor.getPath, predecessor.project)
  }

  protected var classpathEntries: Option[Array[IClasspathEntry]] = None
  protected var relatedWorkspaceProjectNames: Set[String] = Set()
  private[this] var _resolveIssues: Seq[String] = Seq()
  def resolveIssues: Seq[String] = _resolveIssues

  def dependsOnWorkspaceProjects(projectNames: Array[String]) =
    relatedWorkspaceProjectNames.exists(name => projectNames.contains(name))

  protected val settings: Settings = new Settings(path)

  override def getClasspathEntries: Array[IClasspathEntry] =
    classpathEntries match {
      case Some(x) => x
      case None => try {
        // first run, no background etc.
        debug("Evaluating classpath for the first time for project: " + project.getProject().getName())
        val cpInfo = calcClasspath
        val classpathEntriesArray = cpInfo.classpathEntries.toArray
        this.classpathEntries = Some(classpathEntriesArray)
        this.relatedWorkspaceProjectNames = cpInfo.relatedProjects
        this._resolveIssues = cpInfo.resolveIssues

        val sbuildFile = project.getProject().getFile(settings.sbuildFile)

        val sbuildProjectMarkerAttribute = "SBuildProjectMarker"

        val projResource = project.getResource()
        // sbuild file is missing, marker on project
        SimpleJob.scheduleJob(title = "Updating SBuild markers", schedulingRule = Some(projResource)) { monitor =>

          // delete marker
          projResource.findMarkers(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER, false, IResource.DEPTH_ZERO).foreach {
            marker => if (marker.getAttribute(sbuildProjectMarkerAttribute) != null) marker.delete()
          }

          if (!sbuildFile.exists()) {
            error(s"The Build file ${settings.sbuildFile} does not exist.")
            val marker = projResource.createMarker(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER)
            marker.setAttributes(Map(
              IMarker.SEVERITY -> IMarker.SEVERITY_ERROR,
              IMarker.MESSAGE -> s"The Build file ${settings.sbuildFile} does not exist.",
              sbuildProjectMarkerAttribute -> "true"
            ).asJava)
          } else {
            // Report issues to UI
            SimpleJob.scheduleJob(title = "Update SBuild markers", schedulingRule = Some(sbuildFile)) { monitor =>
              monitor.subTask("Updating SBuild markers")

              sbuildFile.deleteMarkers(IMarker.PROBLEM, false, IResource.DEPTH_INFINITE)

              // TODO: parse compiler output and evaluate line of problem, see IMarker.LINE_NUMBER

              _resolveIssues.foreach { msg =>
                info("About to create an error marker: " + msg)
                val problemMarker = sbuildFile.createMarker(IMarker.PROBLEM)
                problemMarker.setAttributes(Map(
                  IMarker.SEVERITY -> IMarker.SEVERITY_ERROR,
                  IMarker.MESSAGE -> msg,
                  IMarker.LINE_NUMBER -> 1
                //                  sbuildProjectMarkerAttribute -> "true"
                ).asJava)
              }

              Status.OK_STATUS
            }
          }

          Status.OK_STATUS
        }

        classpathEntriesArray
      } catch {
        case e: Throwable =>
          error("Could not calculate classpath entries for project " + project.getProject().getName(), e)
          Array()
      }
    }

  case class ClasspathInfo(classpathEntries: Seq[IClasspathEntry], relatedProjects: Set[String], resolveIssues: Seq[String])

  /**
   * @return A tuple of 1. The classpath entries to use, 2. All potentially related Workspace projects
   */
  def calcClasspath: ClasspathInfo = {
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

    val deps = resolver.exportedDependencies(settings.exportedClasspath) match {
      case Right(d) => d
      case Left(error) =>
        return ClasspathInfo(Seq(), Set(), Seq(error))
    }
    debug("Exported dependencies: " + deps.mkString(","))

    val javaModel: IJavaModel = JavaCore.create(project.getProject.getWorkspace.getRoot)

    val aliases = WorkspaceProjectAliases(project)
    debug("Using workspaceProjectAliases: " + aliases)

    /** Resolve through embedded SBuild. Slurping all SBuild errors, but logging them. */
    def resolveViaSBuild(dep: String): Either[String, Seq[IClasspathEntry]] =
      resolver.resolve(dep, new NullProgressMonitor()) match {
        case Failure(e) =>
          debug(s"""Could not resolve dependency "${dep}" of project: ${project.getProject.getName}.""", e)
          Left(s"""Could not resolve dependency "${dep}" of project: ${project.getProject.getName}. """ + e.getLocalizedMessage())
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

          Right(files.map { file =>
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
          })
      }

    var relatedWorkspaceProjectNames: Set[String] = Set()
    var issues: Seq[String] = Seq()

    // We will now check, if some of these targets can be resolved from workspace.
    // - If so, we instead add an classpath entry with the existing and open workspace project
    // - Else, we resolve the depenency and add the result to the classpath
    val classpathEntries =
      deps.flatMap { dep =>
        aliases.getAliasForDependency(dep) match {
          case None => resolveViaSBuild(dep) match {
            case Right(cpe) => cpe
            case Left(error) =>
              issues ++= Seq(error)
              Seq()
          }
          case Some(alias) =>
            relatedWorkspaceProjectNames += alias
            javaModel.getJavaProject(alias) match {
              case javaProject if javaProject.exists && javaProject.getProject.isOpen =>
                debug("Using Workspace Project '" + javaProject.getProject.getName + "' as alias for project: " + dep)
                Seq(JavaCore.newProjectEntry(javaProject.getPath))
              case _ => resolveViaSBuild(dep) match {
                case Right(cpe) => cpe
                case Left(error) =>
                  issues ++= Seq(error)
                  Seq()
              }
            }
        }
      }

    ClasspathInfo(classpathEntries = classpathEntries.distinct, relatedProjects = relatedWorkspaceProjectNames, resolveIssues = issues)
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

  def dependsOnFile(file: IPath): Boolean =
    project.getProject().getFile(settings.sbuildFile).getFullPath() == file

}

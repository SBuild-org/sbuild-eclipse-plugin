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
import java.net.URI
import org.sbuild.eclipse.resolver.{ Either => JEither }

object SBuildClasspathContainer {
  val ContainerName = "de.tototec.sbuild.SBUILD_DEPENDENCIES"
  def SBuildHomeVariableName = "SBUILD_HOME"
  def ContainerDescription = "SBuild Libraries"

  def getSBuildClasspathContainerForResource(resource: IResource): Option[SBuildClasspathContainer] = {
    val javaProj = JavaCore.create(resource.getProject())
    if (javaProj == null) None
    else getSBuildClasspathContainers(javaProj) match {
      case Array() => None
      case containers =>
        Some(containers.head)
    }
  }

  /**
   * Get all SBuildClasspathContainer's that are associated to the given java projects.
   */
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
 * TODO: resolve the classpath in background
 *
 */
class SBuildClasspathContainer(path: IPath, val project: IJavaProject) extends IClasspathContainer {
  import SBuildClasspathContainer._

  debug("Creating new classpath container instance: " + this)

  override val getKind = IClasspathContainer.K_APPLICATION
  override val getDescription = ContainerDescription
  override val getPath = path

  protected var classpathEntries: Option[Array[IClasspathEntry]] = None
  protected var relatedWorkspaceProjectNames: Set[String] = Set()
  private[this] var _resolveIssues: Seq[ResolveIssue] = Seq()
  private[this] var _includedFiles: Seq[String] = Seq()
  def resolveIssues: Seq[ResolveIssue] = _resolveIssues

  def dependsOnWorkspaceProjects(projectNames: Array[String]) =
    relatedWorkspaceProjectNames.exists(name => projectNames.contains(name))

  protected val settings: Settings = new Settings(path)

  val projectName = project.getProject.getName
  protected val projectRootFile: File = project.getProject.getLocation.makeAbsolute.toFile
  protected val buildfile: File = new File(projectRootFile, settings.sbuildFile).getAbsoluteFile
  protected val sbuildFile: IFile = project.getProject().getFile(settings.sbuildFile)

  override def getClasspathEntries(): Array[IClasspathEntry] =
    classpathEntries match {
      case Some(x) => x
      case None => try {
        // first run, no background etc.
        debug(s"${projectName}: Evaluating classpath for the first time based on buildfile: " + buildfile)
        val cpInfo = calcClasspath()
        val classpathEntriesArray = cpInfo.classpathEntries.toArray
        this.classpathEntries = Some(classpathEntriesArray)
        this.relatedWorkspaceProjectNames = cpInfo.relatedProjects
        this._resolveIssues = cpInfo.resolveIssues
        this._includedFiles = cpInfo.includedFiles

        val sbuildProjectMarkerAttribute = "SBuildProjectMarker"

        val projResource = project.getResource()
        // sbuild file is missing, marker on project
        SimpleJob.scheduleJob(title = "Updating SBuild markers", schedulingRule = Some(projResource)) { monitor =>

          // delete marker
          projResource.findMarkers(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER, false, IResource.DEPTH_ZERO).foreach {
            marker => if (marker.getAttribute(sbuildProjectMarkerAttribute) != null) marker.delete()
          }

          // set marker if project file is missing
          if (!sbuildFile.exists()) {
            error(s"${projectName}: The buildfile ${settings.sbuildFile} does not exist.")
            val marker = projResource.createMarker(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER)
            marker.setAttributes(Map(
              IMarker.SEVERITY -> IMarker.SEVERITY_ERROR,
              IMarker.MESSAGE -> s"The buildfile ${settings.sbuildFile} does not exist.",
              sbuildProjectMarkerAttribute -> "true"
            ).asJava)
          }

          Status.OK_STATUS
        }

        if (sbuildFile.exists) {
          // TODO: parse compiler output and evaluate line of problem, see IMarker.LINE_NUMBER

          var sbuildFileMarkersReverse: List[() => Unit] = Nil

          val projUri = projResource.getLocationURI().normalize()

          def getResourceFromFile(filename: String): Option[IFile] = new File(filename) match {
            case x if x.isAbsolute() =>
              val fileUri = x.toURI()
              if (fileUri.toString().startsWith(projUri.toString())) {
                Some(project.getProject().getFile(projUri.relativize(fileUri).toString))
              } else None
            case _ => None
          }

          def addProblem(file: IFile, line: Int, issue: String, severity: Int) = {
            sbuildFileMarkersReverse ::= { () =>
              val problemMarker = file.createMarker(IMarker.PROBLEM)
              problemMarker.setAttributes(Map(
                IMarker.SEVERITY -> severity,
                IMarker.MESSAGE -> issue,
                IMarker.LINE_NUMBER -> line
              //                  sbuildProjectMarkerAttribute -> "true"
              ).asJava)
            }
          }
          def addGenericProblem(issue: String, severity: Int) = addProblem(sbuildFile, 1, issue, severity)
          def addGenericProblem2(file: String, line: Int, issue: String, severity: Int) = addProblem(sbuildFile, 1, s"${file}:${line}: ${issue}", severity)

          _resolveIssues.foreach { msg =>

            msg match {
              case ResolveIssue.ProjectIssue(issue) =>
                new ScalacOutputParser().parse(issue) match {
                  case x if x.isEmpty =>
                    // could not parse specific compiler output, so add a generic marker
                    addGenericProblem(msg.issue, IMarker.SEVERITY_ERROR)

                  case compilerIssues => compilerIssues.foreach {
                    case ScalacOutputParser.Error(file, line, issue) =>
                      getResourceFromFile(file) match {
                        case Some(sbuildFile) => addProblem(sbuildFile, line, issue.mkString("\n"), IMarker.SEVERITY_ERROR)
                        case _ => addGenericProblem2(file, line, issue.mkString("\n"), IMarker.SEVERITY_ERROR)
                      }
                    case ScalacOutputParser.Warning(file, line, issue) =>
                      getResourceFromFile(file) match {
                        case Some(sbuildFile) => addProblem(sbuildFile, line, issue.mkString("\n"), IMarker.SEVERITY_WARNING)
                        case _ => addGenericProblem2(file, line, issue.mkString("\n"), IMarker.SEVERITY_WARNING)
                      }
                  }
                }

              case ResolveIssue.DependencyIssue(issue, dep) =>
                // TODO add marker somewhere
                addGenericProblem(issue, IMarker.SEVERITY_ERROR)
            }

          }

          // Report issues to UI
          SimpleJob.scheduleJob(title = "Update SBuild markers", schedulingRule = Some(sbuildFile)) { monitor =>
            monitor.subTask("Updating SBuild markers")
            sbuildFile.deleteMarkers(IMarker.PROBLEM, false, IResource.DEPTH_INFINITE)
            sbuildFileMarkersReverse.reverse.foreach { marker => marker() }
            Status.OK_STATUS
          }

        }

        classpathEntriesArray
      } catch {
        case e: Throwable =>
          error(s"${projectName}: Could not calculate classpath entries", e)
          Array()
      }
    }

  sealed trait ResolveIssue {
    def issue: String
  }
  object ResolveIssue {
    case class ProjectIssue(override val issue: String) extends ResolveIssue
    case class DependencyIssue(override val issue: String, dependency: String) extends ResolveIssue
  }
  case class ClasspathInfo(classpathEntries: Seq[IClasspathEntry],
                           relatedProjects: Set[String],
                           resolveIssues: Seq[ResolveIssue],
                           includedFiles: Seq[String])

  implicit class EitherSupport[L, R](either: JEither[L, R]) {
    def asScala: Either[L, R] = either match {
      case l if l.isLeft() => Left(l.left())
      case r if r.isRight() => Right(r.right())
    }
  }

  /**
   * @return A tuple of 1. The classpath entries to use, 2. All potentially related Workspace projects
   */
  def calcClasspath(): ClasspathInfo = {
    // TODO: do the heavy work in background (or Job)

    // read the project

    val sbuildHomePath: IPath = JavaCore.getClasspathVariable(SBuildClasspathContainer.SBuildHomeVariableName)
    if (sbuildHomePath == null) {
      throw new RuntimeException("Classpath variable 'SBUILD_HOME' not defined.")
    }
    val sbuildHomeDir = sbuildHomePath.toFile

    info(s"${projectName}: Loading project...")
    val resolver = new SBuildResolver(sbuildHomeDir)

    try {
      resolver.prepareProject(buildfile, keepFailed = true).map { error =>
        debug(s"${projectName}: Could not find usable resolver", error)
        val msg = s"Could not find usable resolver. Cause: ${error.getLocalizedMessage()}"
        return ClasspathInfo(
          classpathEntries = Seq(),
          relatedProjects = Set(),
          resolveIssues = Seq(ResolveIssue.ProjectIssue(msg)),
          includedFiles = Seq())
      }

      // Experimental: Determine included resources via exported dependencies "sbuild.project.includes"
      val includedFiles = resolver.exportedDependencies(buildfile, "sbuild.project.includes").asScala match {
        case Right(includes) =>
          val includedFiles = includes.toSeq
          if (!includedFiles.isEmpty)
            debug(s"${projectName}: Included files: ${includedFiles}")
          includedFiles
        case Left(e) =>
          debug(s"${projectName}: Could not evaluate included files", e)
          Seq()
      }

      val deps = resolver.exportedDependencies(buildfile, settings.exportedClasspath).asScala match {
        case Right(d) => d
        case Left(e) =>
          debug(s"${projectName}: Could not evaluate exported classpath", e)
          val error = s"Could not access exported dependency ${settings.exportedClasspath}. Cause: ${e.getLocalizedMessage()}"
          return ClasspathInfo(
            classpathEntries = Seq(),
            relatedProjects = Set(),
            resolveIssues = Seq(ResolveIssue.ProjectIssue(error)),
            includedFiles = includedFiles)
      }
      debug("Exported dependencies: " + deps.mkString(","))

      val javaModel: IJavaModel = JavaCore.create(project.getProject.getWorkspace.getRoot)

      val aliases = WorkspaceProjectAliases(project)
      debug(s"${projectName}: Using workspaceProjectAliases: ${aliases}")

      /** Resolve through embedded SBuild. Slurping all SBuild errors, but logging them. */
      def resolveViaSBuild(dep: String): Either[String, Seq[IClasspathEntry]] =
        resolver.resolve(buildfile, dep).asScala match {
          case Left(e) =>
            warn(s"${projectName}: Could not resolve dependency: ${dep}", e)
            Left(s"""Could not resolve dependency "${dep}" of project: ${projectName}. """ + e.getLocalizedMessage())
          case Right(filesArray) =>
            val files = filesArray.toSeq
            debug(s"""${projectName}: Resolved dependency "${dep}" to "${files.mkString(", ")}"""")

            var singleSource: Option[File] = None
            var singleJavadoc: Option[File] = None

            if (settings.resolveSources) {
              if (files.size == 1) {
                // resolve sources via "source" scheme handler
                resolver.resolve(buildfile, "source:" + dep).asScala match {
                  case Right(sources) =>
                    if (sources.isEmpty) {
                      debug(s"""${projectName}: Empty sources list for: ${dep}""")
                    } else {
                      if (sources.size > 1) debug(s"""${projectName}: Just using the first file of resolved sources for dep "${dep}" but multiple files were given: ${sources.mkString(", ")}""")
                      debug(s"""${projectName}: Attaching sources for "${dep}": ${sources.head}""")
                      singleSource = Some(sources.head)
                    }
                  case Left(e) =>
                    debug(s"${projectName}: Could not resolve sources for: ${dep}", e)
                }
              } else debug(s"""${projectName}: Skip resolve of sources for "${dep}" as they does not resolved to exactly one file.""")
            }

            if (settings.resolveJavadoc) {
              if (files.size == 1) {
                // resolve javadoc via "javadoc" scheme handler
                resolver.resolve(buildfile, "javadoc:" + dep).asScala match {
                  case Right(javadoc) =>
                    if (javadoc.isEmpty) {
                      debug(s"""${projectName}: Empty javadoc list for: ${dep}""")
                    } else {
                      if (javadoc.size > 1) debug(s"""${projectName}: Just using the first file of resolved javadoc for dep "${dep}" but multiple files were given: ${javadoc.mkString(", ")}""")
                      debug(s"""${projectName}: Attaching javadoc for "${dep}": ${javadoc.head}""")
                      singleSource = Some(javadoc.head)
                    }
                  case Left(e) =>
                    debug(s"""${projectName}: Could not resolve javadoc for: "${dep}"""", e)
                }
              } else debug(s"""${projectName}: Skip resolve of javadoc for "${dep}" as they does not resolved to exactly one file.""")
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
      var issues: Seq[ResolveIssue] = Seq()

      // We will now check, if some of these targets can be resolved from workspace.
      // - If so, we instead add an classpath entry with the existing and open workspace project
      // - Else, we resolve the depenency and add the result to the classpath
      val classpathEntries: Seq[IClasspathEntry] =
        deps.flatMap { dep =>
          aliases.getAliasForDependency(dep) match {
            case None => resolveViaSBuild(dep) match {
              case Right(cpe) => cpe
              case Left(error) =>
                issues ++= Seq(ResolveIssue.DependencyIssue(error, dep))
                Seq()
            }
            case Some(alias) =>
              relatedWorkspaceProjectNames += alias
              javaModel.getJavaProject(alias) match {
                case javaProject if javaProject.exists && javaProject.getProject.isOpen =>
                  debug(s"${projectName}: Using Workspace Project '" + javaProject.getProject.getName + "' as alias for project: " + dep)
                  Seq(JavaCore.newProjectEntry(javaProject.getPath))
                case _ => resolveViaSBuild(dep) match {
                  case Right(cpe) => cpe
                  case Left(error) =>
                    issues ++= Seq(ResolveIssue.DependencyIssue(error, dep))
                    Seq()
                }
              }
          }
        }

      ClasspathInfo(
        classpathEntries = classpathEntries.distinct,
        relatedProjects = relatedWorkspaceProjectNames,
        resolveIssues = issues,
        includedFiles = includedFiles)
    } finally {
      resolver.releaseProject(buildfile)
    }
  }

  def updateClasspath(monitor: IProgressMonitor): Unit = try {
    //    classpathEntries = None
    val newContainer = new SBuildClasspathContainer(this.getPath, this.project)
    monitor.subTask("Updating SBuild library container")
    // Experimental: trigger resolver 
    newContainer.getClasspathEntries()
    debug(s"${projectName}: Updating classpath (by re-setting classpath container)")
    JavaCore.setClasspathContainer(path, Array(project), Array(newContainer), monitor)
  } finally {
    monitor.done()
  }

  def dependsOnResources(resources: Seq[IResource]): Boolean = {
    val projectFileUris = Seq(project.getProject().getFile(settings.sbuildFile).getLocationURI().normalize()) ++
      _includedFiles.map { f => new URI(f).normalize() }

    resources.exists { resource =>
      val depFile = resource.getLocationURI().normalize()
      projectFileUris.exists { fileUri =>
        val found = depFile == fileUri
        // TODO: this log message might be misleading, because it can also apear even if no action is triggered afterwards
        if (found)
          debug(s"""${projectName}: Included file "${depFile}" of project ${project.getProject().getName()} changed.""")
        found
      }
    }

  }

}

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

  override val getKind = IClasspathContainer.K_APPLICATION
  override val getDescription = "SBuild Libraries"
  override val getPath = path

  protected def projectRootFile: File = project.getProject.getLocation.makeAbsolute.toFile

  protected val settings: Settings = new Settings(path)

  protected var sbuildFileTimestamp: Long = 0

  protected var resolveActions: Option[Seq[ResolveAction]] = None

  protected var classpathEntries: Option[Array[IClasspathEntry]] = None

  protected var relatedWorkspaceProjectNames: Set[String] = Set()
  def dependsOnWorkspaceProjects(projectNames: Array[String]) = relatedWorkspaceProjectNames.exists(name => projectNames.contains(name))

  def this(predecessor: SBuildClasspathContainer) {
    this(predecessor.getPath, predecessor.project)
    this.sbuildFileTimestamp = predecessor.sbuildFileTimestamp
    this.resolveActions = predecessor.resolveActions
    this.classpathEntries = predecessor.classpathEntries
    this.relatedWorkspaceProjectNames = predecessor.relatedWorkspaceProjectNames
  }

  /**
   * Read the project and initialize or update the fields: resolveActions and sbuildFileTimestamp.
   * @return <code>true</code> if the project has changed or was read the first time, <code>false</code> otherwise
   */
  def readProject: Boolean = {
    val sbuildHomePath: IPath = JavaCore.getClasspathVariable(SBuildClasspathContainer.SBuildHomeVariableName)
    if (sbuildHomePath == null) {
      throw new RuntimeException("Classpath variable 'SBUILD_HOME' not defined")
    }
    val sbuildHomeDir = sbuildHomePath.toFile

    val reader = SBuildClasspathProjectReader.load(sbuildHomeDir, settings, projectRootFile)

    val buildFile = reader.buildFile

    if (resolveActions.isEmpty) {
      info("Reading project for the first time: " + project.getProject.getName)
    } else if (buildFile.lastModified != sbuildFileTimestamp) {
      // drop previously read actions
      resolveActions = None
      info("Build file has changed. Reading project again: " + project.getProject.getName)
    }

    synchronized {
      if (this.resolveActions.isDefined) {
        // already read the project
        debug("Already read the project")
        false
      } else {
        debug("Reading project and resolve action definitions")

        this.resolveActions = Option(reader.readResolveActions)
        this.sbuildFileTimestamp = buildFile.lastModified()

        true
      }
    }
  }

  def notifyUpdateClasspathEntries(inBackground: Boolean = false) {
    def notify = {
      try {
        val newContainer = new SBuildClasspathContainer(this)
        JavaCore.setClasspathContainer(path, Array(project), Array(newContainer), new NullProgressMonitor())
      } catch {
        case e: Throwable => error("Caught exception while updating the SBuildClasspathContainer for project: " + project, e)
      }
    }
    inBackground match {
      case false => notify
      case _ => new Thread("notify-update-sbuild-classpath") {
        override def run() {
          Thread.sleep(1000)
          if (!isInterrupted()) notify
        }
      }.start
    }
  }

  /**
   * @return A tuple of 1. The classpath entries to use, 2. All potentially related Workspace projects
   */
  def calcClasspath: (Seq[IClasspathEntry], Set[String]) = {
    // TODO: do the heavy work in background (or Job)

    // read the project
    val projectHasChanged: Boolean = readProject

    // Now, we have a Seq of ResolveActions.
    // We will now check, if some of these targets can be resolved from workspace.
    // - If so, we instead add an classpath entry with the existing and open workspace project
    // - Else, we resolve the depenency and add the result to the classpath

    def resolveViaSBuild(action: ResolveAction): IClasspathEntry = {
      val file = new File(action.result) match {
        case f if f.isAbsolute => f
        case f => f.getAbsoluteFile
      }
      if (settings.relaxedFetchOfDependencies && file.exists) {
        debug("Skipping resolve of already existing dependency: " + action.name)
      } else {
        debug("About to resolve dependency: " + action.name)
        val success = action.action()
        if (!success) {
          error("Error in resolve of dependency \"" + action.name + "\" in project: " + project.getProject.getName)
        }
        debug("Resolve successful: " + success)
      }
      debug("About to add classpath entry: " + action.result)
      JavaCore.newLibraryEntry(new Path(file.getCanonicalPath), null /*sourcepath*/ , null)
    }

    val javaModel: IJavaModel = JavaCore.create(project.getProject.getWorkspace.getRoot)

    val aliases = WorkspaceProjectAliases(project)
    debug("Using workspaceProjectAliases: " + aliases)

    var relatedWorkspaceProjectNames: Set[String] = Set()

    val classpathEntries = resolveActions.get.map { action: ResolveAction =>
      aliases.getAliasForDependency(action.name) match {
        case None => resolveViaSBuild(action)
        case Some(alias) =>
          relatedWorkspaceProjectNames += alias
          javaModel.getJavaProject(alias) match {
            case javaProject if javaProject.exists && javaProject.getProject.isOpen =>
              debug("Using Workspace Project '" + javaProject.getProject.getName + "' as alias for project: " + action.name)
              JavaCore.newProjectEntry(javaProject.getPath)
            case _ => resolveViaSBuild(action)
          }
      }
    }

    (classpathEntries.distinct, relatedWorkspaceProjectNames)
  }

  def updateClasspathEntries: Unit = try {
    val (newClasspathEntries, relatedWorkspaceProjectNames) = calcClasspath

    val firstRun = this.classpathEntries.isEmpty
    val classpathEntriesChanged = !firstRun // || this.classpathEntries.get.toSet != newClasspathEntries.toSet

    this.classpathEntries = Some(newClasspathEntries.toArray)
    this.relatedWorkspaceProjectNames = relatedWorkspaceProjectNames

    if (classpathEntriesChanged) {
      debug("Classpath changed for " + project + ". Notify in background.")
      notifyUpdateClasspathEntries(inBackground = true)
    } else {
      debug("Classpath evaluated for the first time or it did not change for " + project)
    }

    // this.classpathEntries.get

  } catch {
    case e: Throwable =>
      error("Could not calculate classpath entries for project " + project.getProject.getName, e)
  }

  override def getClasspathEntries: Array[IClasspathEntry] = {
    this.classpathEntries match {
      case None =>
        updateClasspathEntries
        Array()
      case Some(entries) => entries
    }
  }

}

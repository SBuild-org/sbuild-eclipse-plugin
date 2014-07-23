package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.IWorkspaceRunnable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import scala.parallel.Future
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.CoreException
import org.eclipse.ui.statushandlers.StatusManager
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.ui.internal.Workbench
import org.eclipse.core.runtime.Platform

class WorkspaceProjectChangeListener extends IResourceChangeListener {

  override def resourceChanged(event: IResourceChangeEvent): Unit = try {
    event.getType match {
      case IResourceChangeEvent.POST_CHANGE =>
        event.getDelta match {
          case null =>
          case projDelta =>

            // detect changes to build files

            var changedResources: List[IResource] = List()
            projDelta.accept(new IResourceDeltaVisitor {
              override def visit(delta: IResourceDelta): Boolean = {
                //                info("Examining change: " + delta)
                if ((delta.getFlags() & IResourceDelta.CONTENT) != 0) {
                  val resource = delta.getResource()
                  changedResources ::= resource
                }
                true
              }
            })

            // search for opened/closed projects

            val interestingFlags = IResourceDelta.OPEN | IResourceDelta.CHANGED | IResourceDelta.ADDED
            val openedProjs = getProjects(projDelta.getAffectedChildren(interestingFlags).
              collect { case d if (d.getFlags() & IResourceDelta.OPEN) != 0 => d.getResource() })

            val projectsToUpdate =
              //              projectWithChangedSBuildFiles ++ 
              affectedProjects(openedProjs, changedResources)
            projectsToUpdate.distinct.foreach { c =>
              val job = new RefreshContainerJob(container = c, isUser = false)
              job.schedule()
            }
        }

      case _ => // other cases are not interesting
    }
  } catch {
    case e: CoreException =>
      StatusManager.getManager().handle(e.getStatus())
  }

  /**
   * Get all projects that are associated to the given resources.
   */
  def getProjects(resources: Array[IResource]): Array[IProject] =
    resources.map { r => getProject(r) }.collect { case Some(x) => x }.distinct

  /**
   * Get the project associated to the given resource.
   */
  def getProject(resource: IResource): Option[IProject] = {
    resource.getType match {
      case IResource.PROJECT | IResource.FILE | IResource.FOLDER => Some(resource.getProject)
      case _ => None
    }
  }

  /**
   * Determine which SBuildClasspathContainer's are affected by a change in the given projects and/or resources.
   */
  def affectedProjects(projects: Array[IProject], changedResources: Seq[IResource]): Array[SBuildClasspathContainer] = {
    if (projects.isEmpty && changedResources.isEmpty) Array()
    else {

      val projectNames = projects.map { _.getName }

      if (false /* no debug */ ) {
        if (!projects.isEmpty)
          debug("Changed projects: " + projects.map(p => p.getName + (if (p.isOpen) " opened" else " closed")).mkString(", "))
        if (!changedResources.isEmpty)
          debug("Changed resources: " + changedResources.map(r => r.getName).mkString(", "))
      }

      val workspaceRoot = ResourcesPlugin.getWorkspace.getRoot
      val openJavaProjects = JavaCore.create(workspaceRoot).getJavaProjects.filter(_.getProject.isOpen)
      val sbuildContainers = SBuildClasspathContainer.getSBuildClasspathContainers(openJavaProjects)
      val projectsToUpdate = sbuildContainers.filter { scc =>
        scc.dependsOnWorkspaceProjects(projectNames) || scc.dependsOnResources(changedResources)
      }

      if (!projectsToUpdate.isEmpty)
        debug("Changes affect SBuild projects: " + projectsToUpdate.map { _.project.getProject().getName() }.toSeq)

      projectsToUpdate
    }
  }

}
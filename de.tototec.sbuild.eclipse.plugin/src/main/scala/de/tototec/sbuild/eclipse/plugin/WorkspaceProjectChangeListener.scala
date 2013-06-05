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
      //      case IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.PRE_DELETE =>
      //        val project = getProject(event.getResource)
      //        project.map { p => projectClosed(p) }

      case IResourceChangeEvent.POST_CHANGE =>
        event.getDelta match {
          case null =>
          case projDelta =>

            // detect changes to build files

            var projectWithChangeSBuildFiles: Seq[SBuildClasspathContainer] = Seq()

            //            projDelta.
            projDelta.accept(new IResourceDeltaVisitor {
              override def visit(delta: IResourceDelta): Boolean = {
                //                info("Examining change: " + delta)
                val resource = delta.getResource()
                if ((delta.getFlags() & IResourceDelta.CONTENT) != 0) {

                  //                val resourceFile = ResourcesPlugin.getWorkspace().getRoot().getFile(resource.getFullPath())
                  //                info("Resource: " + resource + ", File: " + resourceFile)
                  val container = SBuildClasspathContainer.getSBuildClasspathContainerForResource(resource)
                  //                  info("Potential container: " + container)
                  container match {
                    case Some(scc) if scc.dependsOnFile(resource.getFullPath()) =>
                      info("Project " + resource.getProject().getName() + " needs to re-evaluate its SBuild configuration based on file: " + resource.getName())
                      projectWithChangeSBuildFiles ++= Seq(scc)
                    //                    case Some(scc) =>
                    //                      info("Project " + resource.getProject().getName() + " does not depends on resource: " + resource.getName())
                    case _ =>
                  }
                }
                true
              }
            })

            // search for opened/closed projects

            val interestingFlags = IResourceDelta.OPEN | IResourceDelta.CHANGED | IResourceDelta.ADDED
            val openedProjs = getProjects(projDelta.getAffectedChildren(interestingFlags).
              collect { case d if (d.getFlags() & IResourceDelta.OPEN) != 0 => d.getResource() })

            val projectsToUpdate = projectWithChangeSBuildFiles ++ affectedProjects(openedProjs)
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

  def getProjects(resources: Array[IResource]): Array[IProject] =
    resources.map { r => getProject(r) }.collect { case Some(x) => x }.distinct

  def getProject(resource: IResource): Option[IProject] = {
    resource.getType match {
      case IResource.PROJECT | IResource.FILE | IResource.FOLDER => Some(resource.getProject)
      case _ => None
    }
  }

  def affectedProjects(projects: Array[IProject]): Array[SBuildClasspathContainer] = {
    if (projects.isEmpty) Array()
    else {

      val projectNames = projects.map { _.getName }

      info("Changed projects: " + projects.map(p => p.getName + (if (p.isOpen) " opened" else " closed")).mkString(", "))

      val workspaceRoot = ResourcesPlugin.getWorkspace.getRoot
      val openJavaProjects = JavaCore.create(workspaceRoot).getJavaProjects.filter(_.getProject.isOpen)
      val sbuildContainers = SBuildClasspathContainer.getSBuildClasspathContainers(openJavaProjects)
      val (projectsToUpdate, projectToIgnore) = sbuildContainers.partition { _.dependsOnWorkspaceProjects(projectNames) }

      if (!projectsToUpdate.isEmpty)
        debug("Updating SBuild Libraries in projects: " + projectsToUpdate.map { _.project.getProject().getName() }.toSeq)

      projectsToUpdate
    }
  }

}
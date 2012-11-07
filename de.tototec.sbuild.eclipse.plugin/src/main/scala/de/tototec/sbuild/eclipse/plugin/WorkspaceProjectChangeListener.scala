package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.core.JavaCore

class WorkspaceProjectChangeListener extends IResourceChangeListener {

  override def resourceChanged(event: IResourceChangeEvent) {
    event.getType match {
//      case IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.PRE_DELETE =>
//        val project = getProject(event.getResource)
//        project.map { p => projectClosed(p) }

      case IResourceChangeEvent.POST_CHANGE =>
        event.getDelta match {
          case null =>
          case projDelta =>
            val openedProjs = getProjects(projDelta.getAffectedChildren(IResourceDelta.OPEN | IResourceDelta.CHANGED | IResourceDelta.ADDED).
              filterNot(d => (d.getFlags() & IResourceDelta.OPEN) == 0).
              map { _.getResource })
            projectsOpened(openedProjs)
        }

      case _ => // other cases are not interesting
    }
  }

  def getProjects(resources: Array[IResource]): Array[IProject] =
    resources.map { r => getProject(r) }.filter(_.isDefined).map(_.get).distinct

  def getProject(resource: IResource): Option[IProject] = {
    resource.getType match {
      case IResource.PROJECT | IResource.FILE | IResource.FOLDER => Some(resource.getProject)
      case _ => None
    }
  }

  def projectClosed(project: IProject) {
    info("Closed project: " + project.getName)
    // TODO: find affected projects and refresh classpath
  }

  def projectsOpened(projects: Array[IProject]) {
    debug("Possibly opened projects: " + projects.map(_.getName).mkString(", "))
    if (!projects.isEmpty) {

      val projectNames = projects.map { _.getName }

      info("Changed projects: " + projects.map(p => p.getName + (if(p.isOpen) " opened" else " closed")).mkString(", "))

      val workspaceRoot = ResourcesPlugin.getWorkspace.getRoot
      val openJavaProjects = JavaCore.create(workspaceRoot).getJavaProjects.filter(_.getProject.isOpen)
      val sbuildContainers = SBuildClasspathContainer.getSBuildClasspathContainers(openJavaProjects)
      sbuildContainers.foreach { c =>
        if (c.dependsOnWorkspaceProjects(projectNames)) {
        	info("Trigger update SBuild Libraries of project: " + c.project.getProject.getName)
        	// trigger a recalculation
        	c.updateClasspathEntries
        }
      }

    }
  }

}
package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ClasspathContainerInitializer
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore

import de.tototec.sbuild.eclipse.plugin.Logger.debug

class SBuildClasspathContainerInitializer extends ClasspathContainerInitializer {

  override def initialize(containerPath: IPath, project: IJavaProject): Unit = {
    debug(s"intialize(containerPath=${containerPath},project=${project.getProject.getName})")
    setClasspathContainer(containerPath, project)
  }

  override def canUpdateClasspathContainer(containerPath: IPath, project: IJavaProject): Boolean = true

  override def requestClasspathContainerUpdate(containerPath: IPath, project: IJavaProject,
                                               containerSuggestion: IClasspathContainer) {
    debug(s"requestClasspathContainerUpdate(containerPath=${containerPath},project=${project.getProject.getName})")
    setClasspathContainer(containerPath, project)
  }

  def setClasspathContainer(containerPath: IPath, project: IJavaProject) {
      val container = new SBuildClasspathContainer(containerPath, project)
      JavaCore.setClasspathContainer(containerPath, Array(project), Array(container), new NullProgressMonitor())
  }

}
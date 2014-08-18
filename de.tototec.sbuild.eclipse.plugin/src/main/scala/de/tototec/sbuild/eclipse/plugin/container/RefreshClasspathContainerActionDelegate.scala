package de.tototec.sbuild.eclipse.plugin.container

import org.eclipse.jdt.core.JavaCore
import org.eclipse.jface.action.IAction
import de.tototec.sbuild.eclipse.plugin.BaseActionDelegate
import de.tototec.sbuild.eclipse.plugin.Logger.debug
import org.eclipse.jface.viewers.ISelection

class RefreshClasspathContainerActionDelegate extends BaseActionDelegate {

  private[this] var containers: Array[SBuildClasspathContainer] = Array()

  override def selectionChanged(action: IAction, selection: ISelection): Unit = {
    super.selectionChanged(action, selection)
    val javaProjects = selectedProjects.map(JavaCore.create(_)).toArray
    containers = SBuildClasspathContainer.getSBuildClasspathContainers(javaProjects)
    action.setEnabled(!containers.isEmpty)
  }

  override def run(action: IAction): Unit = {
    containers foreach { container =>
      debug(s"${container.projectName}: About to refresh classpath container")
      new RefreshContainerJob(container = container, isUser = false).schedule()
    }
  }

}
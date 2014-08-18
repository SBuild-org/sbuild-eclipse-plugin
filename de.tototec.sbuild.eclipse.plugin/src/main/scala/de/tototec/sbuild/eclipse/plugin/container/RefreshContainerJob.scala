package de.tototec.sbuild.eclipse.plugin.container

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Status
import org.eclipse.jdt.core.JavaModelException

import de.tototec.sbuild.eclipse.plugin.Logger.error
import de.tototec.sbuild.eclipse.plugin.SimpleJob

class RefreshContainerJob(container: SBuildClasspathContainer, isUser: Boolean)
  extends SimpleJob("Refresh SBuild Libraries", isUser, None)((monitor: IProgressMonitor) => {
    try {
      monitor.subTask("Updating library pathes")
      container.updateClasspath(monitor)
      Status.OK_STATUS
    } catch {
      case e: JavaModelException =>
        error(s"${container.projectName}: Exception in refresh job.", e)
        e.getStatus()
    }
  }
  ) 
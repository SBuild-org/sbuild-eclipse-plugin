package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.jobs.ISchedulingRule
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Status
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.core.runtime.NullProgressMonitor

//object RefreshContainerRule extends ISchedulingRule {
//  private[this] def sameRule(rule: ISchedulingRule) =
//    // we compare class names instead of instances, to ensure even different classloader
//    this.getClass().getName() == rule.getClass().getName()
//
//  override def contains(rule: ISchedulingRule): Boolean = sameRule(rule)
//  override def isConflicting(rule: ISchedulingRule): Boolean = sameRule(rule)
//}

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
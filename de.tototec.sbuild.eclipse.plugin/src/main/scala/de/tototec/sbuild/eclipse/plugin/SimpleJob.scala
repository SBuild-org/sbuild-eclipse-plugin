package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.jobs.ISchedulingRule
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor

object SimpleJob {

  def scheduleJob(title: String, isUser: Boolean = false, schedulingRule: Option[ISchedulingRule] = None)(action: IProgressMonitor => IStatus): Job = {
    val job = new SimpleJob(title, isUser, schedulingRule)(action)
    job.schedule()
    job
  }

}

class SimpleJob(title: String, isUser: Boolean = false, schedulingRule: Option[ISchedulingRule] = None)(action: IProgressMonitor => IStatus) extends Job(title) {
  setUser(isUser)
  schedulingRule.map { rule => setRule(rule) }
  override def run(monitorOrNull: IProgressMonitor): IStatus = try {
    val monitor = Option(monitorOrNull).getOrElse(new NullProgressMonitor())
    action(monitor)
  } finally {
    Option(monitorOrNull).map(_.done())
  }
}
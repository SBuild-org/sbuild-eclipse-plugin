package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.IProgressMonitor

trait DebugProgressMonitor extends IProgressMonitor {

  abstract override def beginTask(name: String, totalWork: Int) {
    debug(s"Progress: beginTask($name, $totalWork)")
    super.beginTask(name, totalWork)
  }

  abstract override def subTask(subTask: String) {
    debug(s"Progress: subTask($subTask)")
    super.subTask(subTask)
  }

}
package de.tototec.sbuild.eclipse.plugin.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IWorkspace
import de.tototec.sbuild.eclipse.plugin.WorkspaceProjectChangeListener
import org.eclipse.core.internal.runtime.InternalPlatform
import org.eclipse.core.runtime.ILog
import org.eclipse.core.runtime.Platform
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status

object SBuildClasspathActivator {
  private var _activator: SBuildClasspathActivator = _
  def activator = _activator
  def activator_=(activator: SBuildClasspathActivator) = _activator = activator
}

class SBuildClasspathActivator extends BundleActivator {

  private var _bundleContext: Option[BundleContext] = None
  def bundleContext = _bundleContext.get

  private var workspace: Option[IWorkspace] = None
  private var workspaceProjectChangeListener: Option[WorkspaceProjectChangeListener] = None

  override def start(bundleContext: BundleContext) {
    Console.err.println("Starting bundle: " + bundleContext.getBundle)
    this._bundleContext = Some(bundleContext)
    SBuildClasspathActivator.activator = this;

    val workspaceProjectChangeListener = new WorkspaceProjectChangeListener()
    this.workspaceProjectChangeListener = Some(workspaceProjectChangeListener)

    val workspace = ResourcesPlugin.getWorkspace
    this.workspace = Some(workspace)

    workspace.addResourceChangeListener(workspaceProjectChangeListener)

  }

  override def stop(bundleContext: BundleContext) {
    for (
      workspace <- this.workspace;
      projectListener <- this.workspaceProjectChangeListener
    ) {
      workspace.removeResourceChangeListener(projectListener)
    }

    workspaceProjectChangeListener = None
    workspace = None

    SBuildClasspathActivator.activator = null;
    this._bundleContext = null
  }

  def log: ILog = Platform.getLog(bundleContext.getBundle)

  def log(status: Int, msg: String, cause: Throwable = null) {
    log.log(new Status(status, bundleContext.getBundle.getSymbolicName, msg, cause))
  }
}

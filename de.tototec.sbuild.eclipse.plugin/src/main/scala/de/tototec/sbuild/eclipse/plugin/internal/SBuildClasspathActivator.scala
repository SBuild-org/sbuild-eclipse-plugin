package de.tototec.sbuild.eclipse.plugin.internal

import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.ILog
import org.eclipse.core.runtime.Platform
import org.eclipse.core.runtime.Status
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import de.tototec.sbuild.eclipse.plugin.WorkspaceProjectChangeListener
import java.net.URLClassLoader
import de.tototec.sbuild.eclipse.plugin.Classpathes
import java.io.File
import org.eclipse.core.resources.IResourceChangeEvent
import org.sbuild.eclipse.resolver.SBuildResolver
import org.osgi.util.tracker.ServiceTracker
import org.osgi.framework.ServiceReference
import java.util.{ List => JList }
import org.sbuild.eclipse.resolver.{ Either => JEither }

/**
 * Companion object for bundle activator class [[SBuildClasspathActivator]].
 */
object SBuildClasspathActivator {
  /**
   * Access to the current activator instance.
   */
  def activator = _activator.getOrElse {
    throw new IllegalStateException("SBuild Eclipse Plugin not activated.");
  }
  private def activator_=(activator: SBuildClasspathActivator) = _activator = Option(activator)
  private[this] var _activator: Option[SBuildClasspathActivator] = None
}

/**
 * Bundle activator class for the SBuild Eclipse Plugin.
 */
class SBuildClasspathActivator extends BundleActivator {

  private[this] var _bundleContext: Option[BundleContext] = None
  def bundleContext = _bundleContext.get

  private[this] var onStop: List[BundleContext => Unit] = Nil

  private[this] var resolvers: Seq[SBuildResolver] = Seq()

  /**
   * Start of the bundle.
   */
  override def start(bundleContext: BundleContext) {
    SBuildClasspathActivator.activator = this;
    onStop ::= { _ => SBuildClasspathActivator.activator = null }

    de.tototec.sbuild.eclipse.plugin.debug("Starting bundle: " + bundleContext.getBundle)
    this._bundleContext = Some(bundleContext)
    onStop ::= { _ => _bundleContext = None }

    val tracker = new ServiceTracker(bundleContext, classOf[SBuildResolver].getName, null) {
      override def addingService(reference: ServiceReference): AnyRef = {
        de.tototec.sbuild.eclipse.plugin.info("Registering detected SBuild Resover: " + reference)
        val service = bundleContext.getService(reference).asInstanceOf[SBuildResolver]
        synchronized { resolvers ++= Seq(service) }
        service
      }
      override def removedService(reference: ServiceReference, service: AnyRef): Unit = {
        de.tototec.sbuild.eclipse.plugin.info("Unregistering SBuild Resover: " + reference)
        bundleContext.ungetService(reference)
        synchronized { resolvers = resolvers.filter(service.eq) }
      }
    }
    tracker.open();
    onStop ::= { _ => tracker.close() }

    // Register project change listener
    val workspaceProjectChangeListener = new WorkspaceProjectChangeListener()
    val workspace = ResourcesPlugin.getWorkspace
    workspace.addResourceChangeListener(workspaceProjectChangeListener, IResourceChangeEvent.POST_CHANGE)
    onStop ::= { _ => workspace.removeResourceChangeListener(workspaceProjectChangeListener) }

  }

  /** Stop of the bundle. */
  override def stop(bundleContext: BundleContext) = onStop.foreach { f => f(bundleContext) }

  def log: ILog = Platform.getLog(bundleContext.getBundle)

  def log(status: Int, msg: String, cause: Throwable = null) {
    log.log(new Status(status, bundleContext.getBundle.getSymbolicName, msg, cause))
  }

  val sbuildResolver: SBuildResolver = new SBuildResolver {
    override def exportedDependencies(projectFile: File, exportName: String): JEither[Throwable, JList[String]] = {
      if (resolvers.isEmpty) {
        return JEither.left(new RuntimeException("No SBuild Resolvers found."))
      } else {
        val stream = resolvers.toStream.map(_.exportedDependencies(projectFile, exportName))
        stream.find(_.isRight()).getOrElse {
          JEither.left(new RuntimeException("No SBuild Resolver could successfully export. Causes:\n - " + stream.map(_.left().getLocalizedMessage()).mkString("\n - ")))
        }
      }
    }
    override def resolve(projectFile: File, dependency: String): JEither[Throwable, JList[File]] = {
      if (resolvers.isEmpty) {
        return JEither.left(new RuntimeException("No SBuild Resolvers found."))
      } else {
        val stream = resolvers.toStream.map(_.resolve(projectFile, dependency))
        stream.find(_.isRight()).getOrElse {
          JEither.left(new RuntimeException("No SBuild Resolver could successfully export. Causes:\n - " + stream.map(_.left().getLocalizedMessage()).mkString("\n - ")))
        }
      }
    }
  }

}

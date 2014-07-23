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

  /**
   * Start of the bundle.
   */
  override def start(bundleContext: BundleContext) {
    SBuildClasspathActivator.activator = this;
    onStop ::= { _ => SBuildClasspathActivator.activator = null }

    this._bundleContext = Some(bundleContext)
    onStop ::= { _ => _bundleContext = None }

    de.tototec.sbuild.eclipse.plugin.debug("Starting bundle: " + bundleContext.getBundle)

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

  case class CachedClassLoader(sbuildHomeDir: File, classLoader: URLClassLoader)
  
  private[this] var sbuildClassLoader: Option[CachedClassLoader] = None

  def sbuildEmbeddedClassLoader(sbuildHomeDir: File): ClassLoader = {
    sbuildClassLoader match {
      case Some(CachedClassLoader(homeDir, classLoader)) if homeDir.equals(sbuildHomeDir) =>
        de.tototec.sbuild.eclipse.plugin.debug("Using cached SBuild Embedded classloader: " + classLoader.getURLs().toSeq)
        classLoader

      case _ =>
        sbuildClassLoader.map { cache =>
          de.tototec.sbuild.eclipse.plugin.debug("Dropping cached classloader for SBuild Embedded: " + cache.classLoader.getURLs().toSeq)
        }

        val embeddedClasspath = Classpathes.fromFile(new File(sbuildHomeDir, "lib/classpath.properties")).embeddedClasspath
        // TODO: check for changed files, if changed, than drop cached classloader
        val classLoader = new URLClassLoader(embeddedClasspath.map { path => new File(path).toURI.toURL }, getClass.getClassLoader)
        sbuildClassLoader = Some(CachedClassLoader(sbuildHomeDir, classLoader))
        de.tototec.sbuild.eclipse.plugin.debug("Created and cached new SBuild Embedded classloader: " + classLoader.getURLs().toSeq)

        classLoader
    }

  }

}

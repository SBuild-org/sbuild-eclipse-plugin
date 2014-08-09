package org.sbuild.eclipse.resolver.sbuild08.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.sbuild.eclipse.resolver.sbuild08.SBuild08Resolver
import org.sbuild.eclipse.resolver.SBuildResolver
import java.util.Properties
import java.io.File
import org.eclipse.ui.plugin.AbstractUIPlugin

object SBuild08ResolverActivator {
  private var _activator: Option[SBuild08ResolverActivator] = None
  def activator: Option[SBuild08ResolverActivator] = _activator
}

class SBuild08ResolverActivator extends AbstractUIPlugin with BundleActivator {

  override def start(context: BundleContext): Unit = {
    SBuild08ResolverActivator._activator = Some(this)
    super.start(context)

    val sbuildHome = getPreferenceStore().getString("SBUILD_PATH")
    if (sbuildHome != null && sbuildHome.trim().length() > 0) {
      val resolver = new SBuild08Resolver(new File(sbuildHome))
      // TODO: Register a pref change listener, and reregister the service with the changed path
      // TODO: add extra info about resolver in properties
      context.registerService(classOf[SBuildResolver].getName, resolver, null)
    }

  }

  override def stop(context: BundleContext): Unit = {
    SBuild08ResolverActivator._activator = None
    super.stop(context)
  }

}
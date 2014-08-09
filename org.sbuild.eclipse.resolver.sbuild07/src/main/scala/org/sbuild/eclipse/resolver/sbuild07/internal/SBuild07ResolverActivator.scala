package org.sbuild.eclipse.resolver.sbuild07.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.sbuild.eclipse.resolver.sbuild07.SBuild07Resolver
import org.sbuild.eclipse.resolver.SBuildResolver
import java.util.Properties
import java.io.File
import org.eclipse.ui.plugin.AbstractUIPlugin

object SBuild07ResolverActivator {
  private var _activator: Option[SBuild07ResolverActivator] = None
  def activator: Option[SBuild07ResolverActivator] = _activator
}

class SBuild07ResolverActivator extends AbstractUIPlugin with BundleActivator {

  override def start(context: BundleContext): Unit = {
    SBuild07ResolverActivator._activator = Some(this)
    super.start(context)

    val sbuildHome = getPreferenceStore().getString("SBUILD_PATH")
    if (sbuildHome != null && sbuildHome.trim().length() > 0) {
      val resolver = new SBuild07Resolver(new File(sbuildHome))
      // TODO: Register a pref change listener, and reregister the service with the changed path

      // TODO: Use the content of the SBUILD_HOME classpath variable as default for the preferences, if set

      // TODO: add extra info about resolver in properties
      context.registerService(classOf[SBuildResolver].getName, resolver, null)
    }

  }

  override def stop(context: BundleContext): Unit = {
    SBuild07ResolverActivator._activator = None
    super.stop(context)
  }
}
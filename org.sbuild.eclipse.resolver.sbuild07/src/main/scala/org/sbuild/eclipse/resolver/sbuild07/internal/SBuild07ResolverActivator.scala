package org.sbuild.eclipse.resolver.sbuild07.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.sbuild.eclipse.resolver.sbuild07.SBuild07Resolver
import org.sbuild.eclipse.resolver.SBuildResolver
import java.util.Properties
import java.io.File

class SBuild07ResolverActivator extends BundleActivator {

  override def start(context: BundleContext): Unit = {
    // FIXME: get SBUILD HOME from configuration
    val sbuildHomeDir = new File("/usr/share/sbuild")
    val resolver = new SBuild07Resolver(sbuildHomeDir)
    
    context.registerService(classOf[SBuildResolver].getName, resolver, null)
  }

  override def stop(context: BundleContext): Unit = {}
}
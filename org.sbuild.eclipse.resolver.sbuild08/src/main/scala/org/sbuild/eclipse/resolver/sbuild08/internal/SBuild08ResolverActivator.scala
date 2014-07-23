package org.sbuild.eclipse.resolver.sbuild08.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.sbuild.eclipse.resolver.sbuild08.SBuild08Resolver
import org.sbuild.eclipse.resolver.SBuildResolver
import java.util.Properties
import java.io.File

class SBuild08ResolverActivator extends BundleActivator {

  override def start(context: BundleContext): Unit = {
    // FIXME: get SBUILD HOME from configuration
    val sbuildHomeDir = new File("/home/lefou/work/tototec/sbuild/sbuild/sbuild-dist/target/sbuild-0.7.9013")
    val resolver = new SBuild08Resolver(sbuildHomeDir)
    
    // TODO: add extra info about resolver in properties
    context.registerService(classOf[SBuildResolver].getName, resolver, null)
  }

  override def stop(context: BundleContext): Unit = {}
}
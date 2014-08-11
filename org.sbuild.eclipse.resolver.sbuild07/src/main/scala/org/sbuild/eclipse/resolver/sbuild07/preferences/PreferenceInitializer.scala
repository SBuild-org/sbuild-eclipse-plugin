package org.sbuild.eclipse.resolver.sbuild07.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.sbuild.eclipse.resolver.sbuild07.internal.SBuild07ResolverActivator
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore

class PreferenceInitializer() extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences() {
    val sbuildHomePath: IPath = JavaCore.getClasspathVariable("SBUILD_HOME")
    if (sbuildHomePath != null) {
      // In older SBuild versions, we used a classpath variable, this one is present
      val sbuildHomeDir = sbuildHomePath.toFile
      SBuild07ResolverActivator.activator map { activator =>
        val store = activator.getPreferenceStore()
        store.setDefault(Preferences.SBuildHome, sbuildHomeDir.getAbsolutePath())
      }
    }
  }
}
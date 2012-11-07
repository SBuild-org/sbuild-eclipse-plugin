package de.tototec.sbuild.eclipse

import de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator
import org.eclipse.core.runtime.IStatus
package object plugin {

  /** Print a debug message. */
  private[plugin] def debug(msg: => String, cause: Throwable = null) = {
    Console.err.println(msg)
    if (cause != null) {
      Console.err.println(cause.getMessage())
      cause.printStackTrace(Console.err)
    }
    //    SBuildClasspathActivator.activator.log(IStatus.INFO, msg, cause)
  }

  /** Print a info message. */
  private[plugin] def info(msg: => String, cause: Throwable = null) = {
    Console.err.println(msg)
    if (cause != null) {
      Console.err.println(cause.getMessage())
      cause.printStackTrace(Console.err)
    }
    SBuildClasspathActivator.activator.log(IStatus.INFO, msg, cause)
  }

}

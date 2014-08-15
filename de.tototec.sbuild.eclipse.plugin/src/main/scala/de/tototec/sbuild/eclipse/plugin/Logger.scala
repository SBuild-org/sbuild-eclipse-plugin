package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.IStatus
import de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator

object Logger {

  /** Print a debug message. */
  private[plugin] def debug(msg: => String, cause: Throwable = null) = {
    SBuildClasspathActivator.activator.log(IStatus.INFO, msg, cause)
  }

  /** Print an info message. */
  private[plugin] def info(msg: => String, cause: Throwable = null) = {
    SBuildClasspathActivator.activator.log(IStatus.INFO, msg, cause)
  }

  /** Print an error message. */
  private[plugin] def error(msg: => String, cause: Throwable = null) = {
    SBuildClasspathActivator.activator.log(IStatus.ERROR, msg, cause)
  }

  /** Print a warn message. */
  private[plugin] def warn(msg: => String, cause: Throwable = null) = {
    SBuildClasspathActivator.activator.log(IStatus.WARNING, msg, cause)
  }

}
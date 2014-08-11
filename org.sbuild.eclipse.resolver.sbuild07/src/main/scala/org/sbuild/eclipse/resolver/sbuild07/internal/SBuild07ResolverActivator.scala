package org.sbuild.eclipse.resolver.sbuild07.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.sbuild.eclipse.resolver.sbuild07.SBuild07Resolver
import org.sbuild.eclipse.resolver.SBuildResolver
import java.util.Properties
import java.io.File
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.jface.util.IPropertyChangeListener
import org.osgi.framework.ServiceRegistration
import org.eclipse.jface.util.PropertyChangeEvent
import org.sbuild.eclipse.resolver.sbuild07.preferences.Preferences

object SBuild07ResolverActivator {
  private var _activator: Option[SBuild07ResolverActivator] = None
  def activator: Option[SBuild07ResolverActivator] = _activator
}

class SBuild07ResolverActivator extends AbstractUIPlugin with BundleActivator {

  private[this] var onStop: List[BundleContext => Unit] = Nil

  override def start(context: BundleContext): Unit = {
    SBuild07ResolverActivator._activator = Some(this)
    super.start(context)

    var sbuildHome: String = ""
    var ref: Option[ServiceRegistration] = None

    def updateSBuildHome(newSBuildHome: String): Unit = {
      if (newSBuildHome != sbuildHome) {
        sbuildHome = newSBuildHome
        ref foreach { _.unregister() }
        ref = if (sbuildHome != null && sbuildHome.trim().length() > 0) {
          val resolver = new SBuild07Resolver(new File(sbuildHome))
          // TODO: add extra info about resolver in properties
          Some(context.registerService(classOf[SBuildResolver].getName, resolver, null))
        } else None
      }
    }

    val propChangeListener = new IPropertyChangeListener {
      override def propertyChange(event: PropertyChangeEvent): Unit = {
        event.getProperty match {
          case Preferences.SBuildHome => updateSBuildHome(event.getNewValue().asInstanceOf[String])
          case _ =>
        }
      }
    }
    getPreferenceStore().addPropertyChangeListener(propChangeListener)

    onStop ::= { _ => getPreferenceStore().removePropertyChangeListener(propChangeListener) }

    updateSBuildHome(getPreferenceStore().getString(Preferences.SBuildHome))

  }

  override def stop(context: BundleContext): Unit = {
    onStop foreach { f => f(context) }
    SBuild07ResolverActivator._activator = None
    super.stop(context)
  }
}
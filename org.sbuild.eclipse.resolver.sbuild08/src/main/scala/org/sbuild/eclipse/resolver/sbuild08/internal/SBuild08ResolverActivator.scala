package org.sbuild.eclipse.resolver.sbuild08.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.sbuild.eclipse.resolver.sbuild08.SBuild08Resolver
import org.sbuild.eclipse.resolver.SBuildResolver
import java.util.Properties
import java.io.File
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.ServiceRegistration
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent

object SBuild08ResolverActivator {
  private var _activator: Option[SBuild08ResolverActivator] = None
  def activator: Option[SBuild08ResolverActivator] = _activator
}

class SBuild08ResolverActivator extends AbstractUIPlugin with BundleActivator {

 private[this] var onStop: List[BundleContext => Unit] = Nil

  override def start(context: BundleContext): Unit = {
    SBuild08ResolverActivator._activator = Some(this)
    super.start(context)

    var sbuildHome: String = ""
    var ref: Option[ServiceRegistration] = None

    def updateSBuildHome(newSBuildHome: String): Unit = {
      if (newSBuildHome != sbuildHome) {
        sbuildHome = newSBuildHome
        ref foreach { _.unregister() }
        ref = if (sbuildHome != null && sbuildHome.trim().length() > 0) {
          val resolver = new SBuild08Resolver(new File(sbuildHome))
          // TODO: add extra info about resolver in properties
          Some(context.registerService(classOf[SBuildResolver].getName, resolver, null))
        } else None
      }
    }

    val propChangeListener = new IPropertyChangeListener {
      override def propertyChange(event: PropertyChangeEvent): Unit = {
        event.getProperty match {
          case "SBUILD_PATH" => updateSBuildHome(event.getNewValue().asInstanceOf[String])
          case _ =>
        }
      }
    }
    getPreferenceStore().addPropertyChangeListener(propChangeListener)

    onStop ::= { _ => getPreferenceStore().removePropertyChangeListener(propChangeListener) }

    updateSBuildHome(getPreferenceStore().getString("SBUILD_PATH"))

  }

  override def stop(context: BundleContext): Unit = {
    onStop foreach { f => f(context) }
    SBuild08ResolverActivator._activator = None
    super.stop(context)
  }

}
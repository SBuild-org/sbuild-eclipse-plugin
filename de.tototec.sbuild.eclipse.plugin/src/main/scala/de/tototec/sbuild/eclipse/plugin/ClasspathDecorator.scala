package de.tototec.sbuild.eclipse.plugin

import org.eclipse.jface.viewers.ILabelDecorator
import org.eclipse.jface.viewers.ILabelProviderListener
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.viewers.LabelDecorator
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.ILightweightLabelDecorator
import org.eclipse.jface.viewers.IDecoration
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot
import org.eclipse.core.resources.IResource

//class ErrorDecorator extends LabelProvider with ILabelDecorator {
//  override def decorateText(text: String, element: Any): String = text
//  override def decorateImage(image: Image, element: Any): Image = image
//}

class ClasspathDecorator extends ILightweightLabelDecorator {

  // Members declared in org.eclipse.jface.viewers.IBaseLabelProvider 
  override def addListener(listener: ILabelProviderListener) {}
  override def dispose() {}
  override def isLabelProperty(element: Any, property: String): Boolean = false
  override def removeListener(listener: ILabelProviderListener) {}

  // Members declared in org.eclipse.jface.viewers.ILightweightLabelDecorator 
  def decorate(element: Any, decoration: IDecoration) {
    element match {
      case cpc: ClassPathContainer if cpc.getLabel() == SBuildClasspathContainer.ContainerDescription =>
        cpc.getJavaProject()
        val issues = SBuildClasspathContainer.getSBuildClasspathContainers(cpc.getJavaProject()).flatMap(_.resolveIssues)
        if (!issues.isEmpty) decoration.addSuffix(" - Error: " + issues.mkString(" / "))
      //
      //        val res: IResource = null
      //        res.createMarker(arg0)
      //        
      //        cpc.
        

      // jar inside classpath container
      //   case jar: JarPackageFragmentRoot => // no decoration required
      // project inside classpath container
      //   case proj: ClassPathContainer.RequiredProjectWrapper => // no decoration required

      case _ => // no decoration required
    }
  }

}
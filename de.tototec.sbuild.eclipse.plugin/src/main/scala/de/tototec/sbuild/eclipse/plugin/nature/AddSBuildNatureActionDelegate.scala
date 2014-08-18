package de.tototec.sbuild.eclipse.plugin.nature

import org.eclipse.core.resources.IProject
import org.eclipse.jface.action.IAction
import de.tototec.sbuild.eclipse.plugin.Logger.debug
import de.tototec.sbuild.eclipse.plugin.BaseActionDelegate

class AddSBuildNatureActionDelegate() extends BaseActionDelegate {

  //  def isEnabled(project: IProject): Boolean =
  //    project.isAccessible() && !project.hasNature(SBuildProjectNature.NatureId)

  override def run(action: IAction): Unit = {
    selectedProjects foreach { project =>
      debug(s"${project.getName()}: About to run: Add SBuild Nature")
      SBuildProjectNature.ensureSBuildProjectNature(project)
    }
  }

}
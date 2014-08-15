package de.tototec.sbuild.eclipse.plugin.nature

import org.eclipse.ui.actions.ActionDelegate
import org.eclipse.core.resources.IProject
import org.eclipse.jface.action.IAction
import de.tototec.sbuild.eclipse.plugin.Logger._
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.core.resources.IResource
import org.eclipse.ui.IWorkingSet

class AddSBuildNatureActionDelegate() extends ActionDelegate {

  private[this] var selection: Option[ISelection] = None

  def isEnabled(project: IProject): Boolean =
    project.isAccessible() && !project.hasNature(SBuildProjectNature.NatureId)

  override def selectionChanged(action: IAction, selection: ISelection): Unit = this.selection = Option(selection)

  private[this] def selectionToProjects(selection: ISelection): List[IProject] = {
    // we need to update the selected projects
    selection match {
      case structuredSelection: IStructuredSelection =>
        if (structuredSelection.isEmpty()) {
          Nil
        } else {
          structuredSelection.toArray().toList flatMap {
            case p: IProject => List(p)
            case r: IResource => List(r.getProject())
            case w: IWorkingSet => w.getElements().toList flatMap { adaptable =>
              adaptable.getAdapter(classOf[IProject]) match {
                case p: IProject => List(p)
                case _ => adaptable.getAdapter(classOf[IResource]) match {
                  case r: IResource if r.getProject() != null => List(r.getProject())
                  case _ => Nil
                }
              }
            }
            case _ => Nil
          }
        }
      case _ => Nil
    }
    // set action enabled
    //    val enabledForAll = selectedProjects forall { project => isEnabled(project) }
    //    action.setEnabled(enabledForAll)
  }

  override def run(action: IAction): Unit = {
    selection.toList flatMap selectionToProjects foreach { project =>
      debug(s"${project.getName()}: About to run: Add SBuild Nature")
      SBuildProjectNature.ensureSBuildProjectNature(project)
    }
  }

}
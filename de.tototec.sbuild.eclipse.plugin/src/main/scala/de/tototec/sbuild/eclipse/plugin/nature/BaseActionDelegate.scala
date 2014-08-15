package de.tototec.sbuild.eclipse.plugin.nature

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.IWorkingSet
import org.eclipse.ui.actions.ActionDelegate

class BaseActionDelegate() extends ActionDelegate {

  private[this] var selection: Option[ISelection] = None

  override def selectionChanged(action: IAction, selection: ISelection): Unit = this.selection = Option(selection)

  /**
   * A list of all selected (direct and indirect through their sub-resources) projects.
   */
  protected def selectedProjects: List[IProject] = selection.toList flatMap selectionToProjects

  /** All affected projects of this selection. */
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
  }

}
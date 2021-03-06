package de.tototec.sbuild.eclipse.plugin.container

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.ui.wizards.IClasspathContainerPage
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.CellEditor
import org.eclipse.jface.viewers.ColumnLabelProvider
import org.eclipse.jface.viewers.ComboBoxViewerCellEditor
import org.eclipse.jface.viewers.EditingSupport
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.TextCellEditor
import org.eclipse.jface.wizard.WizardPage
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite
import de.tototec.sbuild.eclipse.plugin.Logger.debug
import de.tototec.sbuild.eclipse.plugin.Settings
import de.tototec.sbuild.eclipse.plugin.preferences.SBuildPreferences
import de.tototec.sbuild.eclipse.plugin.preferences.WorkspaceProjectAliases
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy

class SBuildClasspathContainerPage extends WizardPage("SBuild Libraries") with IClasspathContainerPage with IClasspathContainerPageExtension {

  object AliasEntry {
    def apply(key: String, value: String, regex: Boolean) = new AliasEntry(key, value, regex)
    def unapply(e: AliasEntry): Option[(String, String, Boolean)] = Some(e.key, e.value, e.regex)
  }
  class AliasEntry(var key: String, var value: String, var regex: Boolean)

  val containerPath = SBuildClasspathContainer.ContainerName

  private var project: IJavaProject = _
  private var options: Map[String, String] = Map()
  private val settings: Settings = new Settings

  setDescription("Configure SBuild Libraries")
  setPageComplete(true)

  var aliasModel: Seq[AliasEntry] = Seq()

  override def initialize(project: IJavaProject, currentEntries: Array[IClasspathEntry]) = {
    this.project = project
    debug("Read workspace project aliases into " + getClass())
    aliasModel =
      WorkspaceProjectAliases.read(project, SBuildPreferences.Node.WorkspaceProjectAlias).toSeq.map {
        case (key, value) => new AliasEntry(key, value, false)
      } ++
        WorkspaceProjectAliases.read(project, SBuildPreferences.Node.WorkspaceProjectRegexAlias).toSeq.map {
          case (key, value) => new AliasEntry(key, value, true)
        }
  }

  override def setSelection(classpathEntry: IClasspathEntry) = settings.fromIClasspathEntry(classpathEntry)
  override def getSelection(): IClasspathEntry = settings.toIClasspathEntry

  override def finish(): Boolean = {
    debug("Write workspace project aliases from " + getClass())
    val (regex, nonRegex) = aliasModel.partition(_.regex)
    WorkspaceProjectAliases.write(project, SBuildPreferences.Node.WorkspaceProjectRegexAlias, regex.map { case AliasEntry(key, value, true) => (key, value) }.toMap)
    WorkspaceProjectAliases.write(project, SBuildPreferences.Node.WorkspaceProjectAlias, nonRegex.map { case AliasEntry(key, value, false) => (key, value) }.toMap)
    // update the classpath container to reflect changes
    SBuildClasspathContainer.getSBuildClasspathContainers(project).map(c => c.updateClasspath(new NullProgressMonitor()))
    true
  }

  override def createControl(parent: Composite) {

    val composite = new PageComposite(parent, SWT.NONE)
    composite.setLayoutData(new GridData(SWT.BEGINNING | SWT.TOP));

    val sbuildFile = composite.sbuildFileText
    sbuildFile.addModifyListener(new ModifyListener {
      override def modifyText(e: ModifyEvent) {
        settings.sbuildFile = sbuildFile.getText
      }
    })
    sbuildFile.setText(settings.sbuildFile)

    val exportedClasspath = composite.exportedClasspathText
    exportedClasspath.addModifyListener(new ModifyListener {
      override def modifyText(e: ModifyEvent) {
        settings.exportedClasspath = exportedClasspath.getText
      }
    })
    exportedClasspath.setText(settings.exportedClasspath)

    val updateDependenciesButton = composite.updateDependenciesButton
    updateDependenciesButton.setSelection(settings.relaxedFetchOfDependencies)
    updateDependenciesButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) =
        settings.relaxedFetchOfDependencies = updateDependenciesButton.getSelection()
    })

    val resolveSourcesButton = composite.resolveSourcesButton
    resolveSourcesButton.setSelection(settings.resolveSources)
    resolveSourcesButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) =
        settings.resolveSources = resolveSourcesButton.getSelection()
    })

    val resolveJavadocButton = composite.resolveJavadocButton
    resolveJavadocButton.setSelection(settings.resolveJavadoc)
    resolveJavadocButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) =
        settings.resolveJavadoc = resolveJavadocButton.getSelection()
    })

    val workspaceProjectAliases = composite.workspaceProjectAliasTable

    //    new ColumnViewerEditorActivationStrategy(workspaceProjectAliases) {
    //      override protected def isEditorActivationEvent(ev: ColumnViewerEditorActivationEvent): Boolean =
    //        ev.eventType match {
    //          case ColumnViewerEditorActivationEvent.TRAVERSAL |
    //            ColumnViewerEditorActivationEvent.MOUSE_CLICK_SELECTION |
    //            ColumnViewerEditorActivationEvent.MOUSE_DOUBLE_CLICK_SELECTION |
    //            ColumnViewerEditorActivationEvent.PROGRAMMATIC => true
    //          case ColumnViewerEditorActivationEvent.KEY_PRESSED => ev.keyCode == SWT.CR
    //          case _ => false
    //        }
    //    }

    val col1 = new TableViewerColumn(workspaceProjectAliases, SWT.LEFT)
    col1.getColumn.setText("Dependency")
    col1.getColumn.setWidth(200)
    col1.setLabelProvider(new ColumnLabelProvider() {
      override def getText(element: Object) = element match {
        case AliasEntry(key, _, _) => key
        case _ => ""
      }
    })

    val col1EditingSupport = new EditingSupport(workspaceProjectAliases) {
      override def canEdit(o: Object): Boolean = o.isInstanceOf[AliasEntry]
      override def getCellEditor(o: Object): CellEditor = new TextCellEditor(workspaceProjectAliases.getTable)
      override def getValue(o: Object) = o match {
        case AliasEntry(key, _, _) => key
        case _ => ""
      }
      override def setValue(o: Object, newVal: Object) = o match {
        case aliasEntry: AliasEntry if newVal.isInstanceOf[String] =>
          aliasEntry.key = newVal.asInstanceOf[String]
          workspaceProjectAliases.update(o, null)
        case _ =>
      }
    }
    col1.setEditingSupport(col1EditingSupport)

    val col2 = new TableViewerColumn(workspaceProjectAliases, SWT.LEFT)
    col2.getColumn.setText("Workspace Project")
    col2.getColumn.setWidth(200)
    col2.setLabelProvider(new ColumnLabelProvider() {
      override def getText(element: Object) = element match {
        case AliasEntry(_, value, _) => value
        case _ => ""
      }
    })

    val col2EditingSupport = new EditingSupport(workspaceProjectAliases) {
      override def canEdit(o: Object): Boolean = o.isInstanceOf[AliasEntry]
      override def getCellEditor(o: Object): CellEditor = new TextCellEditor(workspaceProjectAliases.getTable)
      override def getValue(o: Object) = o match {
        case AliasEntry(_, value, _) => value
        case _ => ""
      }
      override def setValue(o: Object, newVal: Object) = o match {
        case aliasEntry: AliasEntry if newVal.isInstanceOf[String] =>
          aliasEntry.value = newVal.asInstanceOf[String]
          workspaceProjectAliases.update(o, null)
        case _ =>
      }
    }
    col2.setEditingSupport(col2EditingSupport)

    val col3 = new TableViewerColumn(workspaceProjectAliases, SWT.CENTER)
    col3.getColumn.setText("Regex")
    col3.getColumn.setWidth(20)
    col3.setLabelProvider(new ColumnLabelProvider() {
      override def getText(o: Object) = o match {
        case AliasEntry(_, _, true) => "yes"
        case _ => "no"
      }
    })

    val col3EditingSupport = new EditingSupport(workspaceProjectAliases) {
      override def canEdit(o: Object): Boolean = o.isInstanceOf[AliasEntry]
      override def getCellEditor(o: Object): CellEditor = {
        val combo = new ComboBoxViewerCellEditor(workspaceProjectAliases.getTable)
        combo.setContenProvider(new ArrayContentProvider())
        combo.setLabelProvider(new ColumnLabelProvider() {
          override def getText(element: Any) = element match {
            case java.lang.Boolean.TRUE => "yes"
            case _ => "no"
          }
        })
        combo.setInput(Array(java.lang.Boolean.FALSE, java.lang.Boolean.TRUE))
        combo
      }
      override def getValue(o: Object): Object = o match {
        case AliasEntry(_, _, regex) if regex => java.lang.Boolean.TRUE
        case _ => java.lang.Boolean.FALSE
      }
      override def setValue(o: Object, newVal: Object) = o match {
        case aliasEntry: AliasEntry =>
          newVal match {
            case regex: java.lang.Boolean => aliasEntry.regex = regex
            case _ => aliasEntry.regex = false
          }
          workspaceProjectAliases.update(o, null)
        case _ =>
      }
    }
    col3.setEditingSupport(col3EditingSupport)

    val delButton = composite.removeAliasButton
    delButton.setEnabled(false)

    workspaceProjectAliases.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) {
        val delEnabled = event.getSelection match {
          case sel: IStructuredSelection if !sel.isEmpty => true
          case _ => false
        }
        delButton.setEnabled(delEnabled)
      }
    })

    workspaceProjectAliases.setContentProvider(new ArrayContentProvider())

    workspaceProjectAliases.setInput(aliasModel.toArray)

    composite.addAliasButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) {
        aliasModel ++= Seq(AliasEntry("", "", false))
        workspaceProjectAliases.setInput(aliasModel.toArray)
      }
    })
    delButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) {
        workspaceProjectAliases.getSelection match {
          case sel: IStructuredSelection if !sel.isEmpty =>
            sel.getFirstElement match {
              case entry: AliasEntry =>
                aliasModel = aliasModel.filter(_ != entry)
                workspaceProjectAliases.setInput(aliasModel.toArray)
            }
          case _ =>
        }
      }
    })

    setControl(composite)
  }

}
package de.tototec.sbuild.eclipse.plugin.preferences

import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage

import de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator

class PreferencesPage()
    extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID)
    with IWorkbenchPreferencePage {

  override def init(workbench: IWorkbench): Unit = {
    val prefsStore = SBuildClasspathActivator.activator.getPreferenceStore()
    setPreferenceStore(prefsStore)
    setDescription("SBuild configuration")
  }

  override protected def createFieldEditors(): Unit = {
    def parent() = getFieldEditorParent()

    //    addField(new DirectoryFieldEditor("SBUILD_07_PATH", "SBuild Path (< 0.8)", parent()))
    //    addField(new DirectoryFieldEditor("SBUILD_08_PATH", "SBuild Path (0.8+)", parent()))
  }

}
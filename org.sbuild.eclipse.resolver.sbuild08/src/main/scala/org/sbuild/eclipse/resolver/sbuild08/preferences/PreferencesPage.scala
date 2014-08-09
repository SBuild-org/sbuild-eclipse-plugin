package org.sbuild.eclipse.resolver.sbuild08.preferences

import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.sbuild.eclipse.resolver.sbuild08.internal.SBuild08ResolverActivator

class PreferencesPage()
  extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID)
  with IWorkbenchPreferencePage {

  override def init(workbench: IWorkbench): Unit = {
    SBuild08ResolverActivator.activator.map { activator =>
      val prefsStore = activator.getPreferenceStore()
      setPreferenceStore(prefsStore)
    }
    setDescription("SBuild Resolver for Version 0.8.x and compatible")
  }

  override protected def createFieldEditors(): Unit = {
    addField(new DirectoryFieldEditor("SBUILD_PATH", "SBuild Path", getFieldEditorParent()))
  }

}
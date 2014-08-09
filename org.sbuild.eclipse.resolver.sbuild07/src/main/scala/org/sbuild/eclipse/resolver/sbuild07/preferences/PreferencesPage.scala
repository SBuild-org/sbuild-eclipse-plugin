package org.sbuild.eclipse.resolver.sbuild07.preferences

import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.core.runtime.Platform
import org.sbuild.eclipse.resolver.sbuild07.internal.SBuild07ResolverActivator

class PreferencesPage()
  extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID)
  with IWorkbenchPreferencePage {

  override def init(workbench: IWorkbench): Unit = {
    SBuild07ResolverActivator.activator.map { activator =>
      val prefsStore = activator.getPreferenceStore()
      setPreferenceStore(prefsStore)
    }
    setDescription("SBuild Resolver for Version 0.7.x and before")
  }

  override protected def createFieldEditors(): Unit = {
    addField(new DirectoryFieldEditor("SBUILD_PATH", "SBuild Path", getFieldEditorParent()))
  }

}
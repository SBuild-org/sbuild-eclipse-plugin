package de.tototec.sbuild.eclipse.plugin.nature

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectNature
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import de.tototec.sbuild.eclipse.plugin.Logger.debug
import de.tototec.sbuild.eclipse.plugin.container.SBuildClasspathContainer
import org.eclipse.core.internal.content.ContentType
import org.eclipse.jdt.internal.core.ClasspathEntry
import org.eclipse.jdt.core.IClasspathEntry

object SBuildProjectNature {
  val NatureId = "de.tototec.sbuild.eclipse.plugin.SBUILD_NATURE"

  def ensureSBuildProjectNature(project: IProject): Unit = {
    val description = project.getDescription()
    val currentNatureIds = description.getNatureIds()
    if (!currentNatureIds.contains(NatureId)) {
      debug(s"Adding SBuild Nature to project: ${project.getName()}")
      val newNatureIds = currentNatureIds ++ Array(NatureId)
      description.setNatureIds(newNatureIds)
      project.setDescription(description, new NullProgressMonitor())
      project.touch(new NullProgressMonitor())
    }
  }

  def removeSBuildProjectNature(project: IProject): Unit = {
    val description = project.getDescription()
    val currentNatureIds = description.getNatureIds()
    if (currentNatureIds.contains(NatureId)) {
      debug(s"removing SBuild Nature from project: ${project.getName()}")
      val newNatureIds = currentNatureIds filterNot (NatureId ==)
      description.setNatureIds(newNatureIds)
      project.setDescription(description, new NullProgressMonitor())
      project.touch(new NullProgressMonitor())
    }
  }
}

class SBuildProjectNature() extends IProjectNature {

  private[this] var project: IProject = _
  override def getProject(): IProject = project
  override def setProject(project: IProject): Unit = this.project = project

  override def configure(): Unit = {
    debug(s"${project.getName()}: Configuring new SBuildProjectNature")

    // TODO: Register a builder for the project file

    JavaCore.create(project) match {
      case javaProject: IJavaProject => // add the SBuild Classpath Container
        // check if it is already present
        val existingContainers = SBuildClasspathContainer.getSBuildClasspathContainers(javaProject)
        if (existingContainers.isEmpty) {
          // if not, add it
          debug(s"${project.getName()}: Adding SBuild classpath container to Java project")
          val rawCp = javaProject.getRawClasspath()
          val newRawCp = rawCp ++ Array(JavaCore.newContainerEntry(new Path(SBuildClasspathContainer.ContainerName)))
          javaProject.setRawClasspath(newRawCp, new NullProgressMonitor())
          javaProject.save(new NullProgressMonitor(), true /*force*/ )
        } else {
          debug(s"${project.getName()}: Java project already has an SBuild classpath container")
        }
      case _ => // not a Java project, for now, we wont do anything now
    }
  }

  override def deconfigure(): Unit = {
    debug(s"${project.getName()}: Deconfiguring SBuildProjectNature")
    JavaCore.create(project) match {
      case javaProject: IJavaProject => // remove all SBuild classpath containers
        debug(s"${project.getName()}: removing all SBuild classpath containers from Java project")
        val rawCp = javaProject.getRawClasspath()
        val newRawCp = rawCp filterNot (entry =>
          entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER &&
            entry.getPath().segment(0) == Path.fromPortableString(SBuildClasspathContainer.ContainerName).segment(0))
        debug(s"${project.getName()}: old path: ${rawCp.toSeq} // new path: ${newRawCp.toSeq}")
        javaProject.setRawClasspath(newRawCp, new NullProgressMonitor())
        javaProject.save(new NullProgressMonitor(), true /*force*/ )
      case _ => // not a Java project, nothing to do
    }
  }

}
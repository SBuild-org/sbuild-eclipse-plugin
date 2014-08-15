package de.tototec.sbuild.eclipse.plugin.nature

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectNature
import org.eclipse.jdt.core.JavaCore
import de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator
import de.tototec.sbuild.eclipse.plugin.SBuildClasspathContainer
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import de.tototec.sbuild.eclipse.plugin.Logger._
import org.eclipse.jdt.core.IJavaProject

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

          //          val initializer = Option(JavaCore.getClasspathContainerInitializer(SBuildClasspathContainer.ContainerName))
          //          initializer match {
          //            case None =>
          //              error(s"${project.getName()}: Could not get the SBuildClasspathContainerInitializer")
          //            case Some(i) =>
          //              val path = new Path(SBuildClasspathContainer.ContainerName)
          //              i.initialize(path, javaProject)
          //          }
        } else {
          debug(s"${project.getName()}: Java project already has an SBuild classpath container")
        }
      case _ => // not a Java project, for now, we wont do anything now
    }
  }

  override def deconfigure(): Unit = {
    Option(JavaCore.create(project)) foreach { javaProject =>
      // todo: remove all SBuild classpath containers
    }
  }

}
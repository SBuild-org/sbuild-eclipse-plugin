package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.resources.ProjectScope
import org.eclipse.jdt.core.IJavaProject

import de.tototec.sbuild.eclipse.plugin.Logger.debug

object WorkspaceProjectAliases {

  def apply(project: IJavaProject): WorkspaceProjectAliases = {
    new WorkspaceProjectAliases(
      read(project, SBuildPreferences.Node.WorkspaceProjectAlias),
      read(project, SBuildPreferences.Node.WorkspaceProjectRegexAlias)
    )
  }

  def read(project: IJavaProject, node: String): Map[String, String] = {
    val projectName = project.getProject.getName
    val projectScope = new ProjectScope(project.getProject)
    projectScope.getNode(SBuildPreferences.Node.Main) match {
      case null =>
        debug(s"${projectName}: Could not access prefs node: ${SBuildPreferences.Node.Main}")
        Map()
      case prefs =>
        prefs.node(node) match {
          case null =>
            debug(s"${projectName}: Could not access prefs node: ${node}")
            Map()
          case prefs =>
            val keys = prefs.keys
            if (!keys.isEmpty)
              debug(s"${projectName}: Found aliases (${node}) in prefs for the following dependencies: ${keys.mkString(", ")}")
            keys.map {
              name => (name -> prefs.get(name, ""))
            }.filter {
              case (key, value) => value != ""
            }.toMap
        }
    }
  }

  def write(project: IJavaProject, node: String, aliases: Map[String, String]) {
    val projectScope = new ProjectScope(project.getProject)
    projectScope.getNode(SBuildPreferences.Node.Main) match {
      case null =>
      case prefs =>
        prefs.node(node) match {
          case null =>
          case prefs =>
            prefs.keys.foreach { key => prefs.remove(key) }
            aliases.foreach {
              case (key, "") => // ignore empty alias
              case (key, value) => prefs.put(key, value)
            }
        }
        prefs.flush
    }
  }

}

class WorkspaceProjectAliases(aliases: Map[String, String], regexAliases: Map[String, String]) {

  def getAliasForDependency(dependency: String): Option[String] = {
    aliases.get(dependency).orElse {
      // search a regex
      regexAliases.keys.collectFirst {
        case key if dependency.matches(key) =>
          val Regex = key.r
          Regex.replaceFirstIn(dependency, regexAliases(key))
      }
    }
  }

  override def toString = getClass.getSimpleName +
    "(aliases=" + aliases.size +
    ",regexAliases=" + regexAliases.size +
    ")"
}
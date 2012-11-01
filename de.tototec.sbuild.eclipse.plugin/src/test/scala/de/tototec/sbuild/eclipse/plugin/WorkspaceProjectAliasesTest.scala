package de.tototec.sbuild.eclipse.plugin

import org.scalatest.FunSuite

class WorkspaceProjectAliasesTest extends FunSuite {

  val workspaceAliases = new WorkspaceProjectAliases(aliases = Map(),
    regexAliases = Map(""".*/de\.tototec\.sbuild-.*\.jar""" -> "de.tototec.sbuild"))

  test("dependency should match the only regex alias") {
    val alias = workspaceAliases.getAliasForDependency("http://sbuild.tototec.de/sbuild/attachments/download/20/de.tototec.sbuild-0.1.4.jar")
    assert(alias === Some("de.tototec.sbuild"))
  }

}
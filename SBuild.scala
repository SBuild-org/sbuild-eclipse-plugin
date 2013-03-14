import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
class SBuild(implicit _project: Project) {

  val modules = Modules("de.tototec.sbuild.eclipse.plugin")

  Target("phony:clean") dependsOn modules.map(_("clean"))
  Target("phony:all") dependsOn modules.map(_("all"))

}

import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.3.0")
class SBuild(implicit _project: Project) {

  val tClean = Target("phony:clean")
  val tAll = Target("phony:all")

  val modules = Seq("de.tototec.sbuild.eclipse.plugin")
  modules.foreach { module =>
    Module(module)
    tClean dependsOn module + "::clean"
    tAll dependsOn module + "::all"
  }

}

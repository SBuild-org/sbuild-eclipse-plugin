import org.sbuild._

@version("0.7.9013")
@classpath("/home/lefou/work/tototec/sbuild/javac/org.sbuild.plugins.javac/target/org.sbuild.plugins.javac-0.0.9001.jar",
  "/home/lefou/work/tototec/sbuild/sbuild-bndjar-plugin/org.sbuild.plugins.bndjar/target/org.sbuild.plugins.bndjar-0.0.9000.jar")
class SBuild(implicit _project: Project) {

  val namespace = "org.sbuild.eclipse.resolver"
  val version = "0.3.0"

  Target("phony:clean").evictCache exec {
    Path("target").deleteRecursive
  }

  import org.sbuild.plugins.javac._
  val javac = Plugin[Javac]("main").get

  import org.sbuild.plugins.bndjar._
  Plugin[BndJar]("main") configure (_.
    bndLib("mvn:biz.aQute.bnd:bndlib:2.1.0").
    jarFile(Path(s"target/${namespace}-${version}.jar")).
    classesDirs(javac.targetDir).
    dependsOn(javac.compileTargetName).
    props(Map(
      "Bundle-SymbolicName" -> namespace,
      "Bundle-Version" -> version,
      "Export-Package" -> s"""${namespace};version="${version}"""",
      "Private-Package" -> s"""${namespace}.*""")))
}

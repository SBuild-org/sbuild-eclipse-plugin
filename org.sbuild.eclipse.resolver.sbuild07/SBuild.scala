import org.sbuild._

@version("0.7.9013")
@classpath("/home/lefou/work/tototec/sbuild/sbuild-scalac-plugin/org.sbuild.plugins.scalac/target/org.sbuild.plugins.scalac-0.0.9000.jar",
  "/home/lefou/work/tototec/sbuild/sbuild-bndjar-plugin/org.sbuild.plugins.bndjar/target/org.sbuild.plugins.bndjar-0.0.9000.jar")
class SBuild(implicit _project: Project) {

  val namespace = "org.sbuild.eclipse.resolver.sbuild07"
  val version = "0.1.0"
  val scalaVersion = "2.10.4"

  val compileCp = s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
    "../org.sbuild.eclipse.resolver/target/main.jar" ~
    "mvn:org.osgi:org.osgi.core:4.1.0"

  Target("phony:clean").evictCache exec {
    Path("target").deleteRecursive
  }

  val scalac = Plugin[plugins.scalac.Scalac]("main") configure (_.
    scalaVersion(scalaVersion).
    classpath(compileCp))

  Plugin[plugins.bndjar.BndJar]("main") configure (_.
    bndLib("mvn:biz.aQute.bnd:bndlib:2.1.0").
    classpath(compileCp).
    classesDirs(scalac.get.targetDir).
    dependsOn(scalac.get.compileTargetName).
    props(Map(
      "Bundle-SymbolicName" -> namespace,
      "Bundle-Version" -> version,
      "Bundle-Activator" -> "${namespace}.internal.SBuild07ResolverActivator",
      "Export-Package" -> s"""${namespace};version="${version}"""",
      "Private-Package" -> s"""${namespace}.*""",
      "Import-Package" -> s"""org.sbuild.eclipse.resolver;version="$${range;[==,=+)}",
                              scala.*;version="$${range;[==,=+)}",
                              *""")))
}

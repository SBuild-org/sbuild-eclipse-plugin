import org.sbuild._

@version("0.7.9010")
@classpath("/home/lefou/work/tototec/sbuild/sbuild-scalac-plugin/org.sbuild.plugins.scalac/target/org.sbuild.plugins.scalac-0.0.9000.jar",
  "/home/lefou/work/tototec/sbuild/sbuild-bndjar-plugin/org.sbuild.plugins.bndjar/target/org.sbuild.plugins.bndjar-0.0.9000.jar")
class SBuild(implicit _project: Project) {

  val namespace = "org.sbuild.eclipse.resolver.sbuild07"
  val version = "0.1.0.9000-" + java.text.MessageFormat.format("{0,date,yyyy-MM-dd-HH-mm-ss}", new java.util.Date())
  val scalaVersion = "2.10.4"

  val resolverModule = Module("../org.sbuild.eclipse.resolver")

  val compileCp = s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
    resolverModule.targetRef("jar-main") ~
    "mvn:org.osgi:org.osgi.core:4.1.0"

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:clean").evictCache exec {
    Path("target").deleteRecursive
  }

  val scalac = Plugin[plugins.scalac.Scalac]("main") configure (_.
    scalaVersion(scalaVersion).
    classpath(compileCp))

  Plugin[plugins.bndjar.BndJar]("main") configure (_.
    jarFile(Path(s"target/${namespace}-${version}.jar")).
    bndLib("mvn:biz.aQute.bnd:bndlib:2.1.0").
    classpath(compileCp).
    classesDirs(scalac.get.targetDir).
    dependsOn(scalac.get.compileTargetName).
    props(Map(
      "Bundle-SymbolicName" -> namespace,
      "Bundle-Version" -> version,
      "Bundle-Activator" -> s"${namespace}.internal.SBuild07ResolverActivator",
      "Export-Package" -> s"""${namespace};version="${version}"""",
      "Private-Package" -> s"""${namespace}.*""",
      "Import-Package" -> s"""org.sbuild.eclipse.resolver;provide:=true,
                              scala.*;provide:=true,
                              *""",
      "DynamicImport-Package" -> """!scala.tools.*,
                                    scala.*;version="[2.10,2.10.49)"""",
      "SBuild-Service" -> "org.sbuild.eclipse.resolver.SBuildResolver")))
}

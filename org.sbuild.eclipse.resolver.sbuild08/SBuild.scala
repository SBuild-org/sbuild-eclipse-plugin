import org.sbuild._

@version("0.7.9010")
@classpath("/home/lefou/work/tototec/sbuild/sbuild-scalac-plugin/org.sbuild.plugins.scalac/target/org.sbuild.plugins.scalac-0.0.9000.jar",
  "/home/lefou/work/tototec/sbuild/sbuild-bndjar-plugin/org.sbuild.plugins.bndjar/target/org.sbuild.plugins.bndjar-0.0.9000.jar")
class SBuild(implicit _project: Project) {

  val namespace = "org.sbuild.eclipse.resolver.sbuild08"
  val version = "0.1.0.9000-" + java.text.MessageFormat.format("{0,date,yyyy-MM-dd-HH-mm-ss}", new java.util.Date())
  val scalaVersion = "2.11.2"
  val scalaBinVersion = "2.11"

  val resolverModule = Module("../org.sbuild.eclipse.resolver")

  val compileCp = s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
    resolverModule.targetRef("jar-main") ~
    "mvn:org.osgi:org.osgi.core:4.1.0" ~
    "mvn:org.eclipse:swt:3.3.0-v3346" ~
    "zip:file=swt-debug.jar;archive=http://archive.eclipse.org/eclipse/downloads/drops/R-3.3-200706251500/swt-3.3-gtk-linux-x86_64.zip" ~
    "mvn:org.eclipse.core:resources:3.3.0-v20070604" ~
    "mvn:org.eclipse.core:jobs:3.3.0-v20070423" ~
    "mvn:org.eclipse.equinox:common:3.3.0-v20070426" ~
    "mvn:org.eclipse.core:contenttype:3.2.100-v20070319" ~
    "mvn:org.eclipse.core:commands:3.3.0-I20070605-0010" ~
    "mvn:org.eclipse.equinox:registry:3.3.0-v20070522" ~
    "mvn:org.eclipse.equinox:preferences:3.2.100-v20070522" ~
    "mvn:org.eclipse.core:runtime:3.3.100-v20070530" ~
    "mvn:org.eclipse:jface:3.3.0-I20070606-0010" ~
    "mvn:org.eclipse.ui:workbench:3.3.0-I20070608-1100"

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
      "Bundle-SymbolicName" -> s"${namespace};singleton:=true",
      "Bundle-ActivationPolicy" -> "lazy",
      "Bundle-Version" -> version,
      "Bundle-Activator" -> s"${namespace}.internal.SBuild08ResolverActivator",
      "Export-Package" -> s"""${namespace};version="${version}"""",
      "Private-Package" -> s"""${namespace}.*""",
      "Import-Package" -> s"""org.sbuild.eclipse.resolver;provide:=true,
                              scala.*;provide:=true,
                              *""",
      "DynamicImport-Package" -> """!scala.tools.*,
                                    scala.*;version="[2.11,2.11.49)"""",
      "SBuild-Service" -> "org.sbuild.eclipse.resolver.SBuildResolver",
      "Include-Resource" -> """src/main/resources""",
      "-removeheaders" -> "Include-Resource")))
}

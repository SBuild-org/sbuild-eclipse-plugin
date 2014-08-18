import org.sbuild._

@version("0.7.9010")
@classpath("/home/lefou/work/tototec/sbuild/sbuild-scalac-plugin/org.sbuild.plugins.scalac/target/org.sbuild.plugins.scalac-0.0.9000.jar",
  "/home/lefou/work/tototec/sbuild/sbuild-bndjar-plugin/org.sbuild.plugins.bndjar/target/org.sbuild.plugins.bndjar-0.0.9000.jar",
  "http://sbuild.org/uploads/sbuild/0.7.9010.0-8-0-M1/org.sbuild.addons-0.7.9010.0-8-0-M1.jar")
class SBuild(implicit _project: Project) {

  val namespace = "de.tototec.sbuild.eclipse.plugin"

  val version = "0.4.3.9000-" + java.text.MessageFormat.format("{0,date,yyyy-MM-dd-HH-mm-ss}", new java.util.Date())
  // val version = "0.4.3"
  val eclipseJar = s"target/${namespace}_${version}.jar"

  val scalaVersion = "2.11.2"
  val scalaBinVersion = "2.11"

  val eclipse34zip = "http://archive.eclipse.org/eclipse/downloads/drops/R-3.4-200806172000/eclipse-RCP-3.4-win32-x86_64.zip"

  val resolverModule = Module("../org.sbuild.eclipse.resolver")

  val compilerCp =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
      s"mvn:org.scala-lang:scala-compiler:${scalaVersion}" ~
      s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
      // sbuildCoreJar ~
      // sbuildRunnerJar ~
      // "mvn:org.osgi:org.osgi.core:4.2.0" ~
      "mvn:org.eclipse:osgi:3.3.0-v20070530" ~
      "mvn:org.eclipse.core:runtime:3.3.100-v20070530" ~
      "mvn:org.eclipse.core:resources:3.3.0-v20070604" ~
      "mvn:org.eclipse.core:jobs:3.3.0-v20070423" ~
      "mvn:org.eclipse.equinox:common:3.3.0-v20070426" ~
      "mvn:org.eclipse.core:contenttype:3.2.100-v20070319" ~
      "mvn:org.eclipse:jface:3.3.0-I20070606-0010" ~
      "mvn:org.eclipse.ui:workbench:3.3.0-I20070608-1100" ~
      s"zip:file=eclipse/plugins/org.eclipse.jface_3.4.0.I20080606-1300.jar;archive=$eclipse34zip" ~
      "mvn:org.eclipse:swt:3.3.0-v3346" ~
      "mvn:org.eclipse.jdt:core:3.3.0-v_771" ~
      "mvn:org.eclipse.jdt:ui:3.3.0-v20070607-0010" ~
      "mvn:org.eclipse.core:commands:3.3.0-I20070605-0010" ~
      "mvn:org.eclipse.equinox:registry:3.3.0-v20070522" ~
      "mvn:org.eclipse.equinox:preferences:3.2.100-v20070522" ~
      "zip:file=swt-debug.jar;archive=http://archive.eclipse.org/eclipse/downloads/drops/R-3.3-200706251500/swt-3.3-gtk-linux-x86_64.zip" ~
      "mvn:de.tototec:de.tototec.cmdoption:0.2.1" ~
      "mvn:org.slf4j:slf4j-api:1.7.1" ~
      resolverModule.targetRef("jar-main")
      

  val testCp =
    compileCp ~
      s"mvn:org.scalatest:scalatest_${scalaBinVersion}:2.2.0" ~
      s"mvn:org.scala-lang.modules:scala-xml_${scalaBinVersion}:1.0.2"     

  ExportDependencies("eclipse.classpath", testCp)

  Target("phony:clean").evictCache exec {
    Path("target").deleteRecursive
  }

  val mainScalac = Plugin[plugins.scalac.Scalac]("main") configure (_.
    scalaVersion(scalaVersion).
    classpath(compileCp).
    deprecation(true)
  )

  val mainJar = Plugin[plugins.bndjar.BndJar]("main") configure (_.
    jarFile(Path(eclipseJar)).
    classesDirs(mainScalac.get.targetDir).
    dependsOn(mainScalac.get.compileTargetName).
    classpath(compileCp).
    bndLib("mvn:biz.aQute.bnd:bndlib:2.1.0").
    props(Map(
      "Bundle-SymbolicName" -> s"${namespace};singleton:=true",
      "Bundle-Version" -> version,
      "Bundle-Activator" -> s"${namespace}.internal.SBuildClasspathActivator",
      "Bundle-ActivationPolicy" -> "lazy",
      "Implementation-Version" -> "${Bundle-Version}",
      "Private-Package" -> s"""${namespace},
                               ${namespace}.builder,
                               ${namespace}.container,
                               ${namespace}.nature,
                               ${namespace}.preferences,
                               ${namespace}.internal""",
      "Import-Package" -> """!de.tototec.sbuild.*,
                             !de.tototec.cmdoption.*,
                             org.eclipse.core.runtime;registry=!;common=!;version="3.3.0",
                             org.eclipse.core.internal.resources,
                             org.slf4j.*;resolution:=optional,
                             org.sbuild.eclipse.resolver;provide:=true,
                             scala.*;provide:=true,
                             *""",
      "Include-Resource" -> """src/main/resources""",
      "-removeheaders" -> "Include-Resource",
      "Bundle-RequiredExecutionEnvironment" -> "JavaSE-1.6"
    ))
  )

  val testScalac = Plugin[plugins.scalac.Scalac]("test") configure (_.
    scalaVersion(scalaVersion).
    classpath(testCp ~ mainJar.get.jarFile)
  )

  Target("phony:test") dependsOn mainJar.get.jarFile ~ testCp ~ testScalac.get.compileTargetName exec {
    addons.support.ForkSupport.runJavaAndWait(
      classpath = eclipseJar.files ++ testCp.files,
      arguments = Array("org.scalatest.tools.Runner", "-p", Path("target/test-classes").getPath, "-oF", "-u", Path("target/test-output").getPath)
    )
  }

}

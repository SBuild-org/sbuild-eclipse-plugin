import de.tototec.sbuild._

@version("0.7.1")
class SBuild(implicit _project: Project) {

  val scalaVersion = "2.11.2"

  val eclipse34zip = "http://archive.eclipse.org/eclipse/downloads/drops/R-3.4-200806172000/eclipse-RCP-3.4-win32-x86_64.zip"

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
      // sbuildCoreJar ~
      // sbuildRunnerJar ~
      //      "mvn:org.osgi:org.osgi.core:4.2.0" ~
      //      "mvn:org.osgi:org.osgi.compendium:4.2.0" ~
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
      "../org.sbuild.eclipse.resolver/target/org.sbuild.eclipse.resolver-0.3.0.jar"

  val testCp =
    compileCp ~
      "mvn:org.scalatest:scalatest_2.11:2.2.0"

  ExportDependencies("eclipse.classpath", testCp)

}

import de.tototec.sbuild._

@version("0.7.1")
class SBuild(implicit _project: Project) {

  val deps =
    "mvn:org.scala-lang:scala-library:2.10.4" ~
      "../org.sbuild.eclipse.resolver/target/org.sbuild.eclipse.resolver-0.2.0.jar" ~
      "mvn:org.osgi:org.osgi.core:4.1.0"

  ExportDependencies("eclipse.classpath", deps)
}

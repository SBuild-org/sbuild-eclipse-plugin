import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.1.4")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.2/scala-compiler-2.9.2.jar",
  "http://dl.dropbox.com/u/2590603/bnd/biz.aQute.bnd.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("http", new HttpSchemeHandler())
  SchemeHandler("mvn", new MvnSchemeHandler())
  SchemeHandler("zip", new ZipSchemeHandler())

  val version = Prop("SBUILD_ECLIPSE_VERSION", "0.1.4.9000")
  val sbuildVersion = Prop("SBUILD_VERSION", version)
  val eclipseJar = "target/de.tototec.sbuild.eclipse.plugin-" + version + ".jar"

  val featureXml = "target/feature/feature.xml"
  val featureJar = "target/de.tototec.sbuild.eclipse.plugin.feature_" + version + ".jar"

  val scalaVersion = "2.9.2"

  val eclipse34zip = "http://archive.eclipse.org/eclipse/downloads/drops/R-3.4-200806172000/eclipse-RCP-3.4-win32-x86_64.zip"

  val sbuildCoreJar = "http://sbuild.tototec.de/sbuild/attachments/download/20/de.tototec.sbuild-0.1.4.jar"

  val compileCp =
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
      sbuildCoreJar ~
      "mvn:org.osgi:org.osgi.core:4.2.0" ~
      "mvn:org.eclipse.core:runtime:3.3.100-v20070530" ~
      "mvn:org.eclipse.core:resources:3.3.0-v20070604" ~
      "mvn:org.eclipse.core:jobs:3.3.0-v20070423" ~
      "mvn:org.eclipse.equinox:common:3.3.0-v20070426" ~
      "mvn:org.eclipse.core:contenttype:3.2.100-v20070319" ~
      "mvn:org.eclipse:jface:3.3.0-I20070606-0010" ~
      ("zip:file=eclipse/plugins/org.eclipse.jface_3.4.0.I20080606-1300.jar;archive=" + eclipse34zip) ~
      "mvn:org.eclipse:swt:3.3.0-v3346" ~
      "mvn:org.eclipse.jdt:core:3.3.0-v_771" ~
      "mvn:org.eclipse.jdt:ui:3.3.0-v20070607-0010" ~
      "mvn:org.eclipse.core:commands:3.3.0-I20070605-0010" ~
      "mvn:org.eclipse.equinox:registry:3.3.0-v20070522" ~
      "mvn:org.eclipse.equinox:preferences:3.2.100-v20070522" ~
      "zip:file=swt-debug.jar;archive=http://archive.eclipse.org/eclipse/downloads/drops/R-3.3-200706251500/swt-3.3-gtk-linux-x86_64.zip" ~
      "http://cmdoption.tototec.de/cmdoption/attachments/download/3/de.tototec.cmdoption-0.1.0.jar"

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:all") dependsOn "clean" ~ eclipseJar

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile") dependsOn (compileCp) exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    AntMkdir(dir = Path(output))
    IfNotUpToDate(srcDir = Path(input), stateDir = Path("target"), ctx = ctx) {
      scala_tools_ant.AntScalac(
        target = "jvm-1.5",
        encoding = "UTF-8",
        deprecation = "on",
        unchecked = "on",
        debugInfo = "vars",
        // this is necessary, because the scala ant tasks outsmarts itself 
        // when more than one scala class is defined in the same .scala file
        force = true,
        srcDir = AntPath(input),
        destDir = Path(output),
        classpath = AntPath(locations = ctx.fileDependencies)
      )
    }
  }

  Target("target/bnd.bnd") dependsOn project.projectFile exec { ctx: TargetContext =>
    val bnd = """
Bundle-SymbolicName: de.tototec.sbuild.eclipse.plugin;singleton:=true
Bundle-Version: """ + version + """
Bundle-Activator: de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator
Bundle-ActivationPolicy: lazy
Implementation-Version: ${Bundle-Version}
Private-Package: \
 de.tototec.sbuild.eclipse.plugin, \
 de.tototec.sbuild.eclipse.plugin.internal
Import-Package: \
 !de.tototec.sbuild.*, \
 !de.tototec.cmdoption.*, \
 org.eclipse.core.runtime;registry=!;common=!;version="3.3.0", \
 org.eclipse.core.internal.resources, \
 *
DynamicImport-Package: \
 !scala.tools.*, \
 scala.*
Include-Resource: """ + Path("src/main/resources") + """,""" + Path("target/bnd-resources") + """
-removeheaders: Include-Resource
Bundle-RequiredExecutionEnvironment: J2SE-1.5
"""
    AntEcho(message = bnd, file = ctx.targetFile.get)
  }

  Target(eclipseJar) dependsOn (compileCp ~ "compile" ~ "target/bnd.bnd") exec { ctx: TargetContext =>
    //     val jarTask = new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
    //     jarTask.addFileset(AntFileSet(dir = Path("."), includes = "LICENSE.txt"))
    //     jarTask.execute

    val bndClasses = "target/bnd-classes"
    val projectReaderLib = "target/bnd-resources/OSGI-INF/projectReaderLib"
    val projectReaderPattern = "**/SBuildClasspathProjectReaderImpl**.class"

    AntDelete(dir = Path(bndClasses))
    new AntCopy(toDir = Path(bndClasses)) {
      addFileset(AntFileSet(dir = Path("target/classes"), excludes = projectReaderPattern))
    }.execute

    AntDelete(dir = Path(projectReaderLib))
    AntMkdir(dir = Path(projectReaderLib))
    new AntCopy(toDir = Path(projectReaderLib)) {
      addFileset(AntFileSet(dir = Path("target/classes"), includes = projectReaderPattern))
    }.execute

    aQute_bnd_ant.AntBnd(
      classpath = bndClasses + "," + ctx.fileDependencies.filter(_.getName.endsWith(".jar")).mkString(","),
      eclipse = false,
      failOk = false,
      exceptions = true,
      files = ctx.fileDependencies.filter(_.getName.endsWith(".bnd")).mkString(","),
      output = ctx.targetFile.get
    )
  }

  Target(featureXml) dependsOn eclipseJar exec { ctx: TargetContext =>

    val pluginSize = Path(eclipseJar).length

    val featureXml = 
"""<?xml version="1.0" encoding="UTF-8"?>
<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License.    
-->
<feature
      id="de.tototec.sbuild.eclipse.plugin.feature"
      label="SBuild Eclipse Plugin Feature"
      version="""" + version + """"
      provider-name="ToToTec GbR"
      plugin="de.tototec.sbuild.eclipse.plugin">

   <description url="http://sbuild.tototec.de/sbuild/projects/sbuild/wiki/SBuildEclipsePlugin">
      Eclipse Integration for SBuild Buildsystem.
   </description>

   <copyright>
      Copyright © 2012 ToToTec GbR, Tobias Roeser
   </copyright>

   <license url="http://www.apache.org/licenses/LICENSE-2.0">
<![CDATA[""" + io.Source.fromFile(Path("LICENSE.txt")).getLines.mkString("\n") + """]]>
   </license>

   <url>
      <update label="SBuild Eclipse Plugin Update-Site" url="http://sbuild.tototec.de/svn/sbuild/release/"/>
      <discovery label="SBuild Eclipse Plugin Update-Site" url="http://www.apache.org/dist/ant/ivyde/updatesite"/>
   </url>

   <requires>
      <import plugin="org.eclipse.core.runtime" version="3.4.0" match="compatible"/>
      <import plugin="org.eclipse.jdt.core" version="3.4.0" match="compatible"/>
      <import plugin="org.eclipse.jdt.ui" version="3.4.0" match="compatible"/>
      <import plugin="org.eclipse.equinox.preferences" version="3.2.100" match="compatible"/>
      <import plugin="org.eclipse.core.resources" version="3.3.0" match="compatible"/>
      <import plugin="org.eclipse.jface" version="3.4.0" match="compatible"/>
      <import plugin="org.scala-ide.scala.library" version="2.9.2" match="compatible"/>
   </requires>

   <plugin
         id="de.tototec.sbuild.eclipse.plugin"
         download-size="""" + pluginSize + """"
         install-size="""" + pluginSize + """"
         version="""" + version + """"/>

</feature>
"""

    AntEcho(message = featureXml, file = ctx.targetFile.get)
  }

  Target(featureJar) dependsOn featureXml exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/feature"))
  }

  Target("phony:update-site") dependsOn featureJar ~ eclipseJar exec {
    AntDelete(dir = Path("target/update-site"))
    AntMkdir(dir = Path("target/update-site/features"))
    AntMkdir(dir = Path("target/update-site/plugins"))
    AntCopy(file = Path(featureJar), toDir = Path("target/update-site/features"))
    AntCopy(file = Path(eclipseJar), toFile = Path("target/update-site/plugins/de.tototec.sbuild.eclipse.plugin_" + version + ".jar")) 

    val siteXml = 
"""<site>
  <description>Update-Site for SBuild Eclipse Plugin.</description>

  <feature
    url="features/de.tototec.sbuild.eclipse.plugin.feature_""" + version + """.jar"
    patch="false"
    id="de.tototec.sbuild.eclipse.plugin.feature"
    version="""" + version + """">

    <category name="SBuild Eclipse Plugin"/>
  </feature>

  <category-def label="SBuild Eclipse Plugin" name="SBuild Eclipse Plugin">
    <description>SBuild Eclipse Plugin</description>
  </category-def>

</site>"""

    AntEcho(message = siteXml, file = Path("target/update-site/site.xml"))
  }

}

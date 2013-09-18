import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.4.0")
@include("FeatureBuilder.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val namespace = "de.tototec.sbuild.eclipse.plugin"

  val version = "0.4.2.9000-" + java.text.MessageFormat.format("{0,date,yyyy-MM-dd-HH-mm-ss}", new java.util.Date())
  // val version = "0.4.2"
  val eclipseJar = s"target/${namespace}_${version}.jar"

  // val sbuildCoreJar = "http://sbuild.tototec.de/sbuild/attachments/download/58/de.tototec.sbuild-0.4.0.jar"
  // val sbuildRunnerJar = "http://sbuild.tototec.de/sbuild/attachments/download/58/de.tototec.sbuild-0.4.0.jar"
  // val sbuildCoreJar = "../../sbuild/de.tototec.sbuild/target/de.tototec.sbuild-0.3.2.9002.jar"

  val featureXml = "target/feature/feature.xml"
  val featureProperties = "target/feature/feature.properties"
  val featureJar = s"target/${namespace}.feature_${version}.jar"

  val scalaLibBundleId = "org.scala-ide.scala.library"
  val scalaLibBundleVersion = "2.10.1.v20130302-092018-VFINAL-33e32179fd"
  val scalaLibBundleName = s"${scalaLibBundleId}_${scalaLibBundleVersion}.jar"
  val scalaLibBundle = s"http://download.scala-ide.org/sdk/e37/scala210/stable/site/plugins/${scalaLibBundleName}"

  val scalaLibFeatureXml = "target/scala-feature/feature.xml"
  val scalaLibFeatureJar = s"target/${namespace}.scala-library.feature_${scalaLibBundleVersion}.jar"

  val updateSiteZip = s"target/${namespace}-${version}.zip"

  val scalaVersion = "2.10.1"

  val eclipse34zip = "http://archive.eclipse.org/eclipse/downloads/drops/R-3.4-200806172000/eclipse-RCP-3.4-win32-x86_64.zip"

  val bndCp = "mvn:biz.aQute:bndlib:1.50.0"

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
      "mvn:de.tototec:de.tototec.cmdoption:0.2.1"

  val testCp =
    compileCp ~
    "mvn:org.scalatest:scalatest_2.10:1.9.1" ~
    s"mvn:org.scala-lang:scala-actors:$scalaVersion"


  ExportDependencies("eclipse.classpath", testCp)

  Target("phony:all") dependsOn eclipseJar ~ "update-site" ~ updateSiteZip ~ "test"

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile").cacheable dependsOn compilerCp ~ compileCp ~ "scan:src/main/scala" exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"

    addons.scala.Scalac(
      deprecation = true, unchecked = true, debugInfo = "vars", target = "jvm-1.6",
      sources = "scan:src/main/scala".files,
      destDir = Path(output),
      compilerClasspath = compilerCp.files,
      classpath = compileCp.files
    )
  }

  Target("phony:jar") dependsOn eclipseJar

  Target(eclipseJar) dependsOn compileCp ~ "compile" ~ bndCp ~ "scan:src/main/resources" exec { ctx: TargetContext =>
    addons.bnd.BndJar(
      bndClasspath = bndCp.files,
      classpath = Seq(Path("target/classes")) ++ compileCp.files,
      destFile = ctx.targetFile.get,
      props = Map(
        "Bundle-SymbolicName" -> s"${namespace};singleton:=true",
        "Bundle-Version" -> version,
        "Bundle-Activator" -> s"${namespace}.internal.SBuildClasspathActivator",
        "Bundle-ActivationPolicy" -> "lazy",
        "Implementation-Version" -> "${Bundle-Version}",
        "Private-Package" -> s"""${namespace},
                                 ${namespace}.internal""",
        "Import-Package" -> """!de.tototec.sbuild.*,
                               !de.tototec.cmdoption.*,
                               org.eclipse.core.runtime;registry=!;common=!;version="3.3.0",
                               org.eclipse.core.internal.resources,
                               *""",
        "DynamicImport-Package" -> """!scala.tools.*,
                                      scala.*""",
        "Include-Resource" -> """src/main/resources""",
        "-removeheaders" -> "Include-Resource",
        "Bundle-RequiredExecutionEnvironment" -> "JavaSE-1.6"
      )
    )
  }

  Target(featureProperties) exec { ctx: TargetContext =>
    // Eclipse Update assume the first line as title, so remove trailing empty lines
    val license = io.Source.fromFile(Path("LICENSE.txt")).getLines.dropWhile(l => l.trim.isEmpty)

    val props = new java.util.Properties()
    props.put("description", "Eclipse Integration for SBuild Buildsystem.")
    props.put("license", license.mkString("\n"))
    props.store(new java.io.FileWriter(ctx.targetFile.get), null)
  }

  Target(featureXml) dependsOn eclipseJar exec { ctx: TargetContext =>

    val updateSiteUrl = "http://sbuild.tototec.de/svn/eclipse-update-site/stable"

    val featureXml = FeatureBuilder.createFeatureXml(
      id = s"${namespace}.feature",
      version = version,
      label = "SBuild Eclipse Plugin Feature",
      providerName = "ToToTec GbR",
      brandingPlugin = namespace,
      license = "%license",
      licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0",
      copyright = "Copyright © 2012, 2013, ToToTec GbR, Tobias Roeser",
      description = "%description",
      descriptionUrl = "http://sbuild.tototec.de/sbuild/projects/sbuild/wiki/SBuildEclipsePlugin",
      featureUrls = Seq(
        FeatureUrl(kind = "update", label = "SBuild Eclipse Update Site", url = updateSiteUrl),
        FeatureUrl(kind = "discovery", label = "SBuild Eclipse Update Site", url = updateSiteUrl)
      ),
      requirements = Seq(
        Requirement(plugin = "org.eclipse.core.runtime", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.jdt.core", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.jdt.ui", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.equinox.preferences", version = "3.2.100", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.core.resources", version = "3.3.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.jface", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.scala-ide.scala.library", version = "2.9.2", versionMatch = "compatible")
      ),
      plugins = Seq(
        Plugin(id = namespace, version = version, file = Path(eclipseJar))
      ),
      featureFileHeader = """   Licensed to the Apache Software Foundation (ASF) under one
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
"""
    )

    AntEcho(message = featureXml, file = ctx.targetFile.get)
  }

  Target(scalaLibFeatureXml) dependsOn scalaLibBundle exec { ctx: TargetContext =>

    val scalaLibBundle = this.scalaLibBundle.files.head
    val updateSiteUrl = "http://sbuild.tototec.de/svn/eclipse-update-site/stable"

    val featureXml = FeatureBuilder.createFeatureXml(
      id = s"${namespace}.scala-library.feature",
      version = scalaLibBundleVersion,
      label = "Scala Library for SBuild Eclipse Plugin Feature",
      providerName = "ToToTec GbR",
      brandingPlugin = scalaLibBundleId,
      license = """      SCALA LICENSE

Copyright (c) 2002-2010 EPFL, Lausanne, unless otherwise specified.
All rights reserved.

This software was developed by the Programming Methods Laboratory of the
Swiss Federal Institute of Technology (EPFL), Lausanne, Switzerland.

Permission to use, copy, modify, and distribute this software in source
or binary form for any purpose with or without fee is hereby granted,
provided that the following conditions are met:

   1. Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.

   3. Neither the name of the EPFL nor the names of its contributors
      may be used to endorse or promote products derived from this
      software without specific prior written permission.


THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
""",
      licenseUrl = "http://scala-lang.org/downloads/license.html",
      copyright = "Copyright © 2012, 2013, ToToTec GbR, Tobias Roeser",
      description = "Scala Library",
      descriptionUrl = "http://sbuild.tototec.de/sbuild/projects/sbuild/wiki/SBuildEclipsePlugin",
      featureUrls = Seq(
        FeatureUrl(kind = "update", label = "SBuild Eclipse Update Site", url = updateSiteUrl),
        FeatureUrl(kind = "discovery", label = "SBuild Eclipse Update Site", url = updateSiteUrl)
      ),
      plugins = Seq(
        Plugin(id = scalaLibBundleId, version = scalaLibBundleVersion, file = scalaLibBundle)
      )
    )

    AntEcho(message = featureXml, file = ctx.targetFile.get)
  }

  Target(featureJar) dependsOn featureXml ~ featureProperties exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/feature"))
  }

  Target(scalaLibFeatureJar) dependsOn scalaLibFeatureXml exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/scala-feature"))
  }

  Target("phony:update-site") dependsOn featureJar ~ scalaLibFeatureJar ~ eclipseJar ~ scalaLibBundle exec { ctx: TargetContext =>

    val scalaLibBundle = this.scalaLibBundle.files.head

    AntDelete(dir = Path("target/update-site"))
    AntMkdir(dir = Path("target/update-site/features"))
    AntMkdir(dir = Path("target/update-site/plugins"))
    AntCopy(file = Path(featureJar), toDir = Path("target/update-site/features"))
    AntCopy(file = Path(scalaLibFeatureJar), toDir = Path("target/update-site/features"))
    AntCopy(file = Path(eclipseJar), toDir = Path("target/update-site/plugins"))
    AntCopy(file = scalaLibBundle, toDir = Path("target/update-site/plugins"))

    val siteXml = s"""<?xml version="1.0" encoding="UTF-8"?>
<site>
  <description>Update-Site for SBuild Eclipse Plugin.</description>

  <feature
      url="features/${namespace}.feature_${version}.jar"
      id="${namespace}.feature"
      version="${version}">
    <category name="SBuild"/>
  </feature>

  <feature
      url="features/${namespace}.scala-library.feature_${scalaLibBundleVersion}.jar"
      id="${namespace}.scala-library.feature"
      version="${scalaLibBundleVersion}">
    <category name="Scala"/>
  </feature>

  <category-def name="SBuild" label="SBuild Eclipse Plugin" />
  <category-def name="Scala" label="Scala Runtime" />

</site>"""

    AntEcho(message = siteXml, file = Path("target/update-site/site.xml"))
  }

  Target(updateSiteZip) dependsOn "update-site" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, baseDir = Path("target"), includes = "update-site/**")
  }

  Target("phony:compileTest").cacheable dependsOn compilerCp ~ eclipseJar ~ testCp ~ "scan:src/test/scala" exec {
    addons.scala.Scalac(
      deprecation = true, unchecked = true, debugInfo = "vars",
      sources = "scan:src/test/scala".files,
      destDir = Path("target/test-classes"),
      compilerClasspath = compilerCp.files,
      classpath = testCp.files ++ eclipseJar.files
    )
  }

  Target("phony:test") dependsOn eclipseJar ~ testCp ~ "compileTest" exec { ctx: TargetContext =>
    de.tototec.sbuild.addons.scalatest.ScalaTest(
      classpath = ctx.fileDependencies,
      runPath = Seq("target/test-classes"),
      reporter = "oF")
  }

}

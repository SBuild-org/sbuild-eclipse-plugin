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

  val version = Prop("SBUILD_ECLIPSE_VERSION", "0.2.0.9000-" + java.text.MessageFormat.format("{0,date,yyyy-MM-dd-HH-mm-ss}", new java.util.Date()))
  val sbuildVersion = Prop("SBUILD_VERSION", version)
  val eclipseJar = "target/de.tototec.sbuild.eclipse.plugin_" + version + ".jar"

  val featureXml = "target/feature/feature.xml"
  val featureProperties = "target/feature/feature.properties"
  val featureJar = "target/de.tototec.sbuild.eclipse.plugin.feature_" + version + ".jar"

  val scalaLibBundleId = "org.scala-ide.scala.library"
  val scalaLibBundleVersion = "2.9.2.v20120330-163119-949a4804e4"
  val scalaLibBundleName = scalaLibBundleId + "_" + scalaLibBundleVersion + ".jar"
  val scalaLibBundle = "http://download.scala-ide.org/sdk/e37/scala29/stable/site/plugins/" + scalaLibBundleName

  val scalaLibFeatureXml = "target/scala-feature/feature.xml"
  val scalaLibFeatureJar = "target/de.tototec.sbuild.eclipse.plugin.scala-library.feature_" + scalaLibBundleVersion + ".jar"

  val updateSiteZip = "target/sbuild-eclipse-plugin-update-site-" + version + ".zip"

  val scalaVersion = "2.9.2"

  val eclipse34zip = "http://archive.eclipse.org/eclipse/downloads/drops/R-3.4-200806172000/eclipse-RCP-3.4-win32-x86_64.zip"

  val sbuildCoreJar = "http://sbuild.tototec.de/sbuild/attachments/download/20/de.tototec.sbuild-0.1.4.jar"

  val compileCp =
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
      sbuildCoreJar ~
      // "mvn:org.osgi:org.osgi.core:4.2.0" ~
      "mvn:org.eclipse:osgi:3.3.0-v20070530" ~
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

  val testCp = compileCp ~ "mvn:org.scalatest:scalatest_2.9.0:1.8"

  ExportDependencies("eclipse.classpath", testCp)

  Target("phony:all") dependsOn eclipseJar ~ "update-site" ~ updateSiteZip

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile") dependsOn (compileCp) exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    IfNotUpToDate(srcDir = Path(input), stateDir = Path("target"), ctx = ctx) {
      AntMkdir(dir = Path(output))
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
        classpath = AntPath(locations = ctx.fileDependencies))
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
      output = ctx.targetFile.get)
  }

  Target(featureProperties) exec { ctx: TargetContext =>
    val props = new java.util.Properties()
    props.put("description", "Eclipse Integration for SBuild Buildsystem.")
    props.put("license", io.Source.fromFile(Path("LICENSE.txt")).getLines.mkString("\n"))
    props.store(new java.io.FileWriter(ctx.targetFile.get), null)
  }

  Target(featureXml) dependsOn eclipseJar exec { ctx: TargetContext =>

    val updateSiteUrl = "http://sbuild.tototec.de/svn/eclipse-update-site/stable"

    val featureXml = FeatureBuilder.createFeatureXml(
      id = "de.tototec.sbuild.eclipse.plugin.feature",
      version = version,
      label = "SBuild Eclipse Plugin Feature",
      providerName = "ToToTec GbR",
      brandingPlugin = "de.tototec.sbuild.eclipse.plugin",
      license = "%license",
      licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0",
      copyright = "Copyright © 2012 ToToTec GbR, Tobias Roeser",
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
        Plugin(id = "de.tototec.sbuild.eclipse.plugin", version = version, file = Path(eclipseJar))
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

    val scalaLibBundle = ctx.fileDependencies.find { _.getName.contains(scalaLibBundleName) }.get
    val updateSiteUrl = "http://sbuild.tototec.de/svn/eclipse-update-site/stable"

    val featureXml = FeatureBuilder.createFeatureXml(
      id = "de.tototec.sbuild.eclipse.plugin.scala-library.feature",
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
      copyright = "Copyright © 2012 ToToTec GbR, Tobias Roeser",
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

    val scalaLibBundle = ctx.fileDependencies.find { _.getName.contains(scalaLibBundleName) }.get

    AntDelete(dir = Path("target/update-site"))
    AntMkdir(dir = Path("target/update-site/features"))
    AntMkdir(dir = Path("target/update-site/plugins"))
    AntCopy(file = Path(featureJar), toDir = Path("target/update-site/features"))
    AntCopy(file = Path(scalaLibFeatureJar), toDir = Path("target/update-site/features"))
    AntCopy(file = Path(eclipseJar), toDir = Path("target/update-site/plugins"))
    AntCopy(file = scalaLibBundle, toDir = Path("target/update-site/plugins"))

    val siteXml = """<?xml version="1.0" encoding="UTF-8"?>
<site>
  <description>Update-Site for SBuild Eclipse Plugin.</description>

  <feature
      url="features/de.tototec.sbuild.eclipse.plugin.feature_""" + version + """.jar"
      id="de.tototec.sbuild.eclipse.plugin.feature"
      version="""" + version + """">
    <category name="SBuild"/>
  </feature>

  <feature
      url="features/de.tototec.sbuild.eclipse.plugin.scala-library.feature_""" + scalaLibBundleVersion + """.jar"
      id="de.tototec.sbuild.eclipse.plugin.scala-library.feature"
      version="""" + scalaLibBundleVersion + """">
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

  Target("phony:compileTest") dependsOn eclipseJar ~ testCp exec { ctx: TargetContext =>
    IfNotUpToDate(Path("src/test/scala"), Path("target"), ctx) {
      AntMkdir(dir = Path("target/test-classes"))
      scala_tools_ant.AntScalac(
        target = "jvm-1.5",
        encoding = "UTF-8",
        deprecation = "on",
        unchecked = "on",
        debugInfo = "vars",
        force = true,
        srcDir = AntPath("src/test/scala"),
        destDir = Path("target/test-classes"),
        classpath = AntPath(locations = ctx.fileDependencies))
    }
  }

  Target("phony:test") dependsOn eclipseJar ~ testCp ~ "compileTest" exec { ctx: TargetContext =>
    de.tototec.sbuild.addons.scalatest.ScalaTest(
      classpath = ctx.fileDependencies,
      runPath = Seq("target/test-classes"),
      reporter = "oF")
  }

}

// TODO: Move this into separate file as soon a SBuild version with @include support is release and can be used.

case class Requirement(plugin: String, version: String, versionMatch: String = "compatible")
case class Plugin(id: String, version: String, file: java.io.File = null)
case class FeatureUrl(kind: String, label: String, url: String)

object FeatureBuilder {

  def createFeatureXml(id: String,
                       version: String,
                       label: String,
                       providerName: String,
                       brandingPlugin: String = null,
                       featureFileHeader: String = null,
                       description: String = null,
                       descriptionUrl: String = null,
                       copyright: String = null,
                       license: String = null,
                       licenseUrl: String = null,
                       plugins: Seq[Plugin] = Seq(),
                       requirements: Seq[Requirement] = Seq(),
                       featureUrls: Seq[FeatureUrl] = Seq()): String = {

    val featureXml = new StringBuilder()
    featureXml.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")

    // Header
    if (featureFileHeader != null) featureXml.append("<!--\n").append(featureFileHeader).append(" -->\n")

    // Feature
    featureXml.append("<feature")
    featureXml.append("\n    id=\"").append(id).append("\"")
    featureXml.append("\n    version=\"").append(version).append("\"")
    featureXml.append("\n    label=\"").append(label).append("\"")
    featureXml.append("\n    provider-name=\"").append(providerName).append("\"")
    if (brandingPlugin != null) featureXml.append("\n    plugin=\"").append(brandingPlugin).append("\"")
    featureXml.append(">\n\n")

    // Description
    if (description != null || descriptionUrl != null) {
      featureXml.append("  <description")
      if (descriptionUrl != null) featureXml.append(" url=\"").append(descriptionUrl).append("\">")
      if (description != null) featureXml.append("<![CDATA[").append(description).append("]]>")
      featureXml.append("</description>\n\n")
    }

    // Copyright
    if (copyright != null) featureXml.append("  <copyright><![CDATA[").append(copyright).append("]]></copyright>\n\n")

    // License
    if (license != null || licenseUrl != null) {
      featureXml.append("  <license")
      if (licenseUrl != null) featureXml.append(" url=\"").append(licenseUrl).append("\"")
      featureXml.append(">")
      if (license != null) featureXml.append("<![CDATA[").append(license).append("]]>")
      featureXml.append("</license>\n\n")
    }

    // URL
    if (!featureUrls.isEmpty) {
      featureXml.append("  <url>\n")
      featureUrls.foreach { url =>
        featureXml.append("    <").append(url.kind)
        featureXml.append(" label=\"").append(url.label).append("\"")
        featureXml.append(" url=\"").append(url.url).append("\"")
        featureXml.append("/>\n")
      }
      featureXml.append("  </url>\n\n")
    }

    // Requires
    if (!requirements.isEmpty) {
      featureXml.append("  <requires>\n")
      requirements.foreach { require =>
        featureXml.append("    <import")
        featureXml.append(" plugin=\"").append(require.plugin).append("\"")
        featureXml.append(" version=\"").append(require.version).append("\"")
        featureXml.append(" match=\"").append(require.versionMatch).append("\"")
        featureXml.append("/>\n")
      }
      featureXml.append("  </requires>\n\n")
    }

    // Plugins
    plugins.foreach { plugin =>
      val size = if (plugin.file != null && plugin.file.exists) plugin.file.length else 0
      featureXml.append("  <plugin\n")
      featureXml.append("      id=\"").append(plugin.id).append("\"\n")
      featureXml.append("      version=\"").append(plugin.version).append("\"\n")
      featureXml.append("      download-size=\"").append(size).append("\"\n")
      featureXml.append("      install-size=\"").append(size).append("\"")
      featureXml.append("/>\n\n")
    }

    // End 
    featureXml.append("\n</feature>\n")

    featureXml.toString
  }

}

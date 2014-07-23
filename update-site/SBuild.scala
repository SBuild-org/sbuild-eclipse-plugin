import org.sbuild._
import org.sbuild.ant._
import org.sbuild.ant.tasks._

@version("0.7.9013")
@include("FeatureBuilder.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4",
  "http://sbuild.org/uploads/sbuild/0.7.9010.0-8-0-M1/org.sbuild.ant-0.7.9010.0-8-0-M1.jar")
class SBuild(implicit _project: Project) {

  val namespace = "de.tototec.sbuild.eclipse.plugin"

  val version = "0.4.3.9000-" + java.text.MessageFormat.format("{0,date,yyyy-MM-dd-HH-mm-ss}", new java.util.Date())
  // val version = "0.4.3"

  val featureXml = "target/feature/feature.xml"
  val featureProperties = "target/feature/feature.properties"
  val featureJar = s"target/${namespace}.feature_${version}.jar"

  val pluginModules = Modules("../org.sbuild.eclipse.resolver",
    // "../org.sbuild.eclipse.resolver.sbuild07",
    "../de.tototec.sbuild.eclipse.plugin"
  )

  val siteJars = pluginModules.map(_.targetRef("jar-main"))

  def extractNameVersion(jar: java.io.File): (String, String) = {
    val parts = jar.getName.split("[-_]", 2)
    val name = parts(0)
    // version part minus the .jar suffix
    val version = parts(1).substring(0, parts(1).length - 4)
    (name, version)
  }

  val scalaLibBundleId = "org.scala-ide.scala.library"
  val scalaLibBundleVersion = "2.10.1.v20130302-092018-VFINAL-33e32179fd"
  val scalaLibBundleName = s"${scalaLibBundleId}_${scalaLibBundleVersion}.jar"
  val scalaLibBundle = s"http://download.scala-ide.org/sdk/e37/scala210/stable/site/plugins/${scalaLibBundleName}"

  val scalaLibFeatureXml = "target/scala-feature/feature.xml"
  val scalaLibFeatureJar = s"target/${namespace}.scala-library.feature_${scalaLibBundleVersion}.jar"

  val updateSiteZip = s"target/${namespace}-update-site-${version}.zip"

  val scalaVersion = "2.10.1"

  Target("phony:all") dependsOn "update-site" ~ updateSiteZip

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target(featureProperties) exec { ctx: TargetContext =>
    // Eclipse Update assume the first line as title, so remove trailing empty lines
    val license = io.Source.fromFile(Path("LICENSE.txt")).getLines.dropWhile(l => l.trim.isEmpty)

    val props = new java.util.Properties()
    props.put("description", "Eclipse Integration for SBuild Buildsystem.")
    props.put("license", license.mkString("\n"))
    ctx.targetFile.get.getParentFile.mkdirs
    props.store(new java.io.FileWriter(ctx.targetFile.get), null)
  }

  Target(featureXml) dependsOn siteJars exec { ctx: TargetContext =>

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
        FeatureUrl(kind = "discovery", label = "SBuild Eclipse Update Site", url = updateSiteUrl)),
      requirements = Seq(
        Requirement(plugin = "org.eclipse.core.runtime", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.jdt.core", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.jdt.ui", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.equinox.preferences", version = "3.2.100", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.core.resources", version = "3.3.0", versionMatch = "compatible"),
        Requirement(plugin = "org.eclipse.jface", version = "3.4.0", versionMatch = "compatible"),
        Requirement(plugin = "org.scala-ide.scala.library", version = "2.10.0", versionMatch = "compatible")),
      plugins = siteJars.files.map { jar =>
        val (name, version) = extractNameVersion(jar)
        Plugin(id = name, version = version, file = jar)
      },
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
""")

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
        FeatureUrl(kind = "discovery", label = "SBuild Eclipse Update Site", url = updateSiteUrl)),
      plugins = Seq(
        Plugin(id = scalaLibBundleId, version = scalaLibBundleVersion, file = scalaLibBundle)))

    AntEcho(message = featureXml, file = ctx.targetFile.get)
  }

  Target(featureJar) dependsOn featureXml ~ featureProperties exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/feature"))
  }

  Target(scalaLibFeatureJar) dependsOn scalaLibFeatureXml exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/scala-feature"))
  }

  Target("phony:update-site") dependsOn featureJar ~ scalaLibFeatureJar ~ siteJars ~ scalaLibBundle exec { ctx: TargetContext =>

    val scalaLibBundle = this.scalaLibBundle.files.head

    AntDelete(dir = Path("target/update-site"))
    AntMkdir(dir = Path("target/update-site/features"))
    AntMkdir(dir = Path("target/update-site/plugins"))
    AntCopy(file = Path(featureJar), toDir = Path("target/update-site/features"))
    AntCopy(file = Path(scalaLibFeatureJar), toDir = Path("target/update-site/features"))
    siteJars.files.map { jar =>
      val (name, version) = extractNameVersion(jar)
      AntCopy(file = jar, toFile = Path(s"target/update-site/plugins/${name}_${version}.jar"))
    }
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

}

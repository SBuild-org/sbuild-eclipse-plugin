
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

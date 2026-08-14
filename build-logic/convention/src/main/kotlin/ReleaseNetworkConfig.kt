import java.net.URI

data class ReleaseNetworkConfig(
    val baseUrl: String,
    val host: String,
) {
    fun networkSecurityConfigXml(): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("<network-security-config>")
            appendLine("""    <base-config cleartextTrafficPermitted="false">""")
            appendLine("        <trust-anchors>")
            appendLine("""            <certificates src="system" />""")
            appendLine("        </trust-anchors>")
            appendLine("    </base-config>")
            appendLine("""    <domain-config cleartextTrafficPermitted="false">""")
            appendLine("""        <domain includeSubdomains="false">$host</domain>""")
            appendLine("        <trust-anchors>")
            appendLine("""            <certificates src="system" />""")
            appendLine("        </trust-anchors>")
            appendLine("    </domain-config>")
            appendLine("</network-security-config>")
        }

    companion object {
        const val REQUIRED_BASE_URL = "https://api.whysoezzy.online"
        private const val REQUIRED_HOST = "api.whysoezzy.online"

        fun parse(baseUrlValue: String?): ReleaseNetworkConfig {
            require(baseUrlValue == REQUIRED_BASE_URL) {
                "BASE_URL_RELEASE must be exactly $REQUIRED_BASE_URL."
            }
            val uri =
                runCatching { URI(baseUrlValue) }.getOrNull()
                    ?: throw IllegalArgumentException("BASE_URL_RELEASE must be a valid absolute HTTPS URL.")
            require(
                uri.isAbsolute &&
                    uri.scheme == "https" &&
                    uri.host == REQUIRED_HOST &&
                    uri.rawUserInfo == null &&
                    uri.rawPath.isEmpty() &&
                    uri.rawQuery == null &&
                    uri.rawFragment == null &&
                    uri.port == -1,
            ) {
                "BASE_URL_RELEASE must be exactly $REQUIRED_BASE_URL."
            }
            return ReleaseNetworkConfig(baseUrlValue, REQUIRED_HOST)
        }
    }
}

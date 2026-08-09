import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.util.Base64

data class ReleaseNetworkConfig(
    val baseUrl: String,
    val host: String,
    val pins: List<String>,
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
            appendLine("        <pin-set>")
            pins.forEach { pin ->
                appendLine("""            <pin digest="SHA-256">$pin</pin>""")
            }
            appendLine("        </pin-set>")
            appendLine("    </domain-config>")
            appendLine("</network-security-config>")
        }

    companion object {
        private val reservedHostSuffixes =
            listOf(
                ".example",
                ".invalid",
                ".localhost",
                ".local",
                ".test",
                ".example.com",
                ".example.net",
                ".example.org",
            )

        fun parse(
            baseUrlValue: String?,
            pinsValue: String?,
        ): ReleaseNetworkConfig {
            require(!baseUrlValue.isNullOrEmpty()) {
                "BASE_URL_RELEASE is required for release packaging."
            }
            require(baseUrlValue == baseUrlValue.trim()) {
                "BASE_URL_RELEASE must not contain leading or trailing whitespace."
            }
            val uri =
                runCatching { URI(baseUrlValue) }.getOrNull()
                    ?: throw IllegalArgumentException("BASE_URL_RELEASE must be a valid absolute HTTPS URL.")
            require(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()) {
                "BASE_URL_RELEASE must be an absolute HTTPS URL."
            }
            require(uri.rawUserInfo == null) {
                "BASE_URL_RELEASE must not contain user information."
            }
            require(uri.rawFragment == null) {
                "BASE_URL_RELEASE must not contain a fragment."
            }
            val host = normalizeHost(uri.host)
            require(isConcreteHost(host)) {
                "BASE_URL_RELEASE host '$host' is reserved, local, or a placeholder."
            }

            val pins = parsePins(pinsValue)
            return ReleaseNetworkConfig(baseUrlValue, host, pins)
        }

        private fun normalizeHost(host: String): String =
            if (host.contains(':')) {
                host.lowercase()
            } else {
                IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase()
            }

        private fun isConcreteHost(host: String): Boolean {
            if (host == "localhost" ||
                host == "example.com" ||
                host == "example.net" ||
                host == "example.org" ||
                host.endsWith('.')
            ) {
                return false
            }
            if (reservedHostSuffixes.any(host::endsWith)) return false
            if (!host.contains('.') && !host.contains(':')) return false
            return if (host.contains(':')) {
                val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
                !(address.isAnyLocalAddress ||
                    address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress ||
                    address.isMulticastAddress ||
                    host.startsWith("2001:db8:", ignoreCase = true))
            } else {
                val octets = host.split('.').map { it.toIntOrNull() }
                if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
                    isPublicIpv4(octets.filterNotNull())
                } else {
                    true
                }
            }
        }

        private fun isPublicIpv4(octets: List<Int>): Boolean {
            val first = octets[0]
            val second = octets[1]
            val third = octets[2]
            return when {
                first == 0 || first == 10 || first == 127 || first >= 224 -> false
                first == 100 && second in 64..127 -> false
                first == 169 && second == 254 -> false
                first == 172 && second in 16..31 -> false
                first == 192 && second == 168 -> false
                first == 192 && second == 0 && third in setOf(0, 2) -> false
                first == 198 && second in 18..19 -> false
                first == 198 && second == 51 && third == 100 -> false
                first == 203 && second == 0 && third == 113 -> false
                else -> true
            }
        }

        private fun parsePins(value: String?): List<String> {
            require(!value.isNullOrEmpty()) {
                "RELEASE_SPKI_PINS is required for release packaging."
            }
            val pins =
                value
                    .split('\n')
                    .map { it.removeSuffix("\r") }
                    .also { lines ->
                        require(lines.none(String::isEmpty)) {
                            "RELEASE_SPKI_PINS must contain one non-empty Base64 SHA-256 pin per line."
                        }
                    }
            require(pins.size >= 2) {
                "RELEASE_SPKI_PINS must contain at least two unique pins."
            }
            require(pins.distinct().size == pins.size) {
                "RELEASE_SPKI_PINS must not contain duplicate pins."
            }
            pins.forEach { pin ->
                require(pin == pin.trim()) {
                    "RELEASE_SPKI_PINS entries must not contain surrounding whitespace."
                }
                val bytes =
                    runCatching { Base64.getDecoder().decode(pin) }.getOrNull()
                        ?: throw IllegalArgumentException("RELEASE_SPKI_PINS contains malformed Base64.")
                require(bytes.size == 32) {
                    "Each RELEASE_SPKI_PINS entry must decode to exactly 32 SHA-256 bytes."
                }
                require(Base64.getEncoder().encodeToString(bytes) == pin) {
                    "RELEASE_SPKI_PINS entries must use canonical padded Base64."
                }
                require(bytes.toSet().size > 1) {
                    "RELEASE_SPKI_PINS contains a known placeholder-style pin."
                }
            }
            return pins
        }
    }
}

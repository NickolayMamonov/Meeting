import java.io.File

data class AndroidSigningInputs(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val expectedCertificateSha256: String,
) {
    init {
        require(keyAlias == REQUIRED_KEY_ALIAS) {
            "Android publishing key alias must be '$REQUIRED_KEY_ALIAS'."
        }
        require(certificateFingerprintPattern.matches(expectedCertificateSha256)) {
            "Android publishing certificate SHA-256 must be exactly 64 hexadecimal characters."
        }
    }

    val normalizedCertificateSha256: String = expectedCertificateSha256.lowercase()

    companion object {
        const val REQUIRED_KEY_ALIAS = "meet-release"
        private val certificateFingerprintPattern = Regex("[0-9A-Fa-f]{64}")
        val propertyNames = propertyNames("ANDROID_RELEASE")
        val snapshotPropertyNames = propertyNames("ANDROID_SNAPSHOT")

        fun parse(values: Map<String, String?>): AndroidSigningInputs =
            parse(values, "ANDROID_RELEASE")

        fun parse(
            values: Map<String, String?>,
            prefix: String = "ANDROID_RELEASE",
        ): AndroidSigningInputs {
            val names = propertyNames(prefix)
            val missing = names.filter { values[it].isNullOrEmpty() }
            require(missing.isEmpty()) {
                "Android publishing signing inputs are incomplete; missing: ${missing.joinToString()}."
            }
            return AndroidSigningInputs(
                storeFile = File(requireNotNull(values["${prefix}_KEYSTORE_FILE"])),
                storePassword = requireNotNull(values["${prefix}_STORE_PASSWORD"]),
                keyAlias = requireNotNull(values["${prefix}_KEY_ALIAS"]),
                keyPassword = requireNotNull(values["${prefix}_KEY_PASSWORD"]),
                expectedCertificateSha256 = requireNotNull(values["${prefix}_CERT_SHA256"]),
            )
        }

        fun normalizeCertificateFingerprint(
            value: String?,
            propertyName: String = "ANDROID_RELEASE_CERT_SHA256",
        ): String {
            require(!value.isNullOrEmpty()) {
                "$propertyName is required."
            }
            require(certificateFingerprintPattern.matches(value)) {
                "$propertyName must be exactly 64 hexadecimal characters."
            }
            return value.lowercase()
        }

        private fun propertyNames(prefix: String): List<String> =
            listOf(
                "${prefix}_KEYSTORE_FILE",
                "${prefix}_STORE_PASSWORD",
                "${prefix}_KEY_ALIAS",
                "${prefix}_KEY_PASSWORD",
                "${prefix}_CERT_SHA256",
            )
    }
}

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

abstract class ValidateAndroidVersionTask : DefaultTask() {
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun validate() {
        versionFile.get().asFile.readAndroidVersion()
    }
}

abstract class ValidateSnapshotPublishingInputsTask : DefaultTask() {
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:InputFile
    abstract val releaseRolesFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val runNumber: Property<String>

    @get:Input
    @get:Optional
    abstract val runAttempt: Property<String>

    @get:Input
    @get:Optional
    abstract val commitSha: Property<String>

    @get:Input
    @get:Optional
    abstract val expectedCertificateSha256: Property<String>

    @get:Input
    abstract val signingPropertyPrefix: Property<String>

    @TaskAction
    fun validate() {
        ReleaseBranchRoles.parseJson(releaseRolesFile.get().asFile.readText(Charsets.UTF_8))
        SnapshotVersion.parse(
            stableVersion = versionFile.get().asFile.readAndroidVersion(),
            runNumber = runNumber.orNull,
            runAttempt = runAttempt.orNull,
            commitSha = commitSha.orNull,
        )
        AndroidSigningInputs.normalizeCertificateFingerprint(
            expectedCertificateSha256.orNull,
            "${signingPropertyPrefix.get()}_CERT_SHA256",
        )
    }
}

abstract class ValidateReleasePublishingInputsTask : DefaultTask() {
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:InputFile
    abstract val releaseRolesFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val baseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val expectedCertificateSha256: Property<String>

    @TaskAction
    fun validate() {
        ReleaseBranchRoles.parseJson(releaseRolesFile.get().asFile.readText(Charsets.UTF_8))
        versionFile.get().asFile.readAndroidVersion()
        ReleaseNetworkConfig.parse(baseUrl.orNull)
        AndroidSigningInputs.normalizeCertificateFingerprint(expectedCertificateSha256.orNull)
    }
}

abstract class GenerateReleaseNetworkSecurityConfigTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val baseUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val baseUrlValue = baseUrl.orNull
        val xml =
            if (baseUrlValue == null) {
                lintOnlyNetworkSecurityConfig()
            } else {
                ReleaseNetworkConfig.parse(baseUrlValue).networkSecurityConfigXml()
            }
        outputDirectory.file("xml/network_security_config.xml").get().asFile.apply {
            parentFile.mkdirs()
            writeText(xml)
        }
    }
}

abstract class GenerateSnapshotBuildMetadataTask : DefaultTask() {
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:InputFile
    abstract val releaseRolesFile: RegularFileProperty

    @get:Input
    abstract val runNumber: Property<String>

    @get:Input
    abstract val runAttempt: Property<String>

    @get:Input
    abstract val commitSha: Property<String>

    @get:Input
    abstract val expectedCertificateSha256: Property<String>

    @get:Input
    abstract val workflowName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val integrationBranch =
            ReleaseBranchRoles.parseJson(releaseRolesFile.get().asFile.readText(Charsets.UTF_8))
                .authority(ReleaseBranchRole.INTEGRATION)
                .branch
        val snapshot =
            SnapshotVersion.parse(
                stableVersion = versionFile.get().asFile.readAndroidVersion(),
                runNumber = runNumber.orNull,
                runAttempt = runAttempt.orNull,
                commitSha = commitSha.orNull,
            )
        val certificate =
            AndroidSigningInputs.normalizeCertificateFingerprint(expectedCertificateSha256.orNull)
        outputFile.get().asFile.writeCanonicalJson(
            linkedMapOf(
                "schemaVersion" to 1,
                "channel" to "snapshot",
                "variant" to "snapshot",
                "applicationId" to "dev.whysoezzy.meet.snapshot",
                "versionName" to snapshot.name,
                "versionCode" to snapshot.code,
                "commitSha" to snapshot.commitSha,
                "sourceBranch" to integrationBranch,
                "workflow" to workflowName.get(),
                "runNumber" to snapshot.runNumber,
                "runAttempt" to snapshot.runAttempt,
                "expectedCertificateSha256" to certificate,
                "signingFingerprint" to certificate,
            ),
        )
    }
}

abstract class GenerateReleaseBuildMetadataTask : DefaultTask() {
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:InputFile
    abstract val releaseRolesFile: RegularFileProperty

    @get:Input
    abstract val baseUrl: Property<String>

    @get:Input
    abstract val commitSha: Property<String>

    @get:Input
    abstract val expectedCertificateSha256: Property<String>

    @get:Input
    abstract val workflowName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val stableBranch =
            ReleaseBranchRoles.parseJson(releaseRolesFile.get().asFile.readText(Charsets.UTF_8))
                .authority(ReleaseBranchRole.STABLE)
                .branch
        val version = versionFile.get().asFile.readAndroidVersion()
        val network = ReleaseNetworkConfig.parse(baseUrl.orNull)
        require(fullReleaseCommitShaPattern.matches(commitSha.get())) {
            "releaseCommitSha must be exactly 40 lowercase hexadecimal characters."
        }
        val certificate =
            AndroidSigningInputs.normalizeCertificateFingerprint(expectedCertificateSha256.orNull)
        outputFile.get().asFile.writeCanonicalJson(
            linkedMapOf(
                "schemaVersion" to 1,
                "channel" to "release",
                "variant" to "release",
                "applicationId" to "dev.whysoezzy.meet",
                "versionName" to version.name,
                "versionCode" to version.code,
                "commitSha" to commitSha.get(),
                "sourceBranch" to stableBranch,
                "workflow" to workflowName.get(),
                "releaseHost" to network.host,
                "releaseBaseUrl" to network.baseUrl,
                "expectedCertificateSha256" to certificate,
                "signingFingerprint" to certificate,
            ),
        )
    }
}

internal fun File.readAndroidVersion(): AndroidVersion =
    AndroidVersion.parseJson(readText(Charsets.UTF_8))

private fun validateSigningIdentity(inputs: AndroidSigningInputs) {
    require(inputs.storeFile.isFile) {
        "ANDROID_RELEASE_KEYSTORE_FILE must point to a readable keystore file."
    }
    val keyStore =
        listOf("PKCS12", "JKS")
            .firstNotNullOfOrNull { type ->
                runCatching {
                    KeyStore.getInstance(type).apply {
                        inputs.storeFile.inputStream().use { load(it, inputs.storePassword.toCharArray()) }
                    }
                }.getOrNull()
            } ?: throw IllegalArgumentException(
            "ANDROID_RELEASE_KEYSTORE_FILE could not be opened with ANDROID_RELEASE_STORE_PASSWORD.",
        )
    require(keyStore.containsAlias(inputs.keyAlias) && keyStore.isKeyEntry(inputs.keyAlias)) {
        "Release keystore must contain the private-key alias '${AndroidSigningInputs.REQUIRED_KEY_ALIAS}'."
    }
    require(
        runCatching {
            keyStore.getKey(inputs.keyAlias, inputs.keyPassword.toCharArray())
        }.getOrNull() != null,
    ) {
        "ANDROID_RELEASE_KEY_PASSWORD could not unlock '${AndroidSigningInputs.REQUIRED_KEY_ALIAS}'."
    }
    val certificate =
        requireNotNull(keyStore.getCertificate(inputs.keyAlias)) {
            "Release keystore alias '${AndroidSigningInputs.REQUIRED_KEY_ALIAS}' has no certificate."
        }
    val publicKey = certificate.publicKey
    require(publicKey is RSAPublicKey && publicKey.modulus.bitLength() >= 4_096) {
        "Release signing certificate must use an RSA key of at least 4096 bits."
    }
    val actualFingerprint =
        MessageDigest
            .getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
    require(actualFingerprint == inputs.normalizedCertificateSha256) {
        "Release signing certificate SHA-256 does not match ANDROID_RELEASE_CERT_SHA256."
    }
}

private fun lintOnlyNetworkSecurityConfig(): String =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <!-- Inert lint-only fixture. Release packaging is gated by validateReleasePublishingInputs. -->
    <network-security-config>
        <base-config cleartextTrafficPermitted="false">
            <trust-anchors>
                <certificates src="system" />
            </trust-anchors>
        </base-config>
    </network-security-config>
    """.trimIndent() + "\n"

private val fullReleaseCommitShaPattern = Regex("[0-9a-f]{40}")

private fun File.writeCanonicalJson(values: LinkedHashMap<String, Any>) {
    parentFile.mkdirs()
    writeText(
        values.entries.joinToString(prefix = "{\n", postfix = "\n}\n", separator = ",\n") { (key, value) ->
            "  ${jsonString(key)}: ${jsonValue(value)}"
        },
    )
}

private fun jsonValue(value: Any): String =
    when (value) {
        is String -> jsonString(value)
        is Number, is Boolean -> value.toString()
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(requireNotNull(it)) }
        else -> error("Unsupported canonical JSON value: ${value::class.java.name}")
    }

private fun jsonString(value: String): String =
    buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else ->
                    if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
            }
        }
        append('"')
    }

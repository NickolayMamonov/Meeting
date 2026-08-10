import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

internal fun Project.configureAndroidPublishing(applicationExtension: ApplicationExtension) {
    val versionFile = rootProject.layout.projectDirectory.file("version.json")
    val stableVersion = versionFile.asFile.readAndroidVersion()
    val snapshotRunNumber = publishingInput("snapshotRunNumber")
    val snapshotRunAttempt = publishingInput("snapshotRunAttempt")
    val snapshotCommitSha = publishingInput("snapshotCommitSha")
    val snapshotSigningValues =
        AndroidSigningInputs.snapshotPropertyNames.associateWith { name ->
            publishingInput(name).orNull
        }
    val releaseCommitSha =
        publishingInput("releaseCommitSha", "GITHUB_SHA")
            .orElse(publishingInput("RELEASE_COMMIT_SHA"))
    val baseUrl = publishingInput("BASE_URL_RELEASE")
    val pins = publishingInput("RELEASE_SPKI_PINS")
    val expectedCertificate = publishingInput("ANDROID_RELEASE_CERT_SHA256")
    val snapshotExpectedCertificate = publishingInput("ANDROID_SNAPSHOT_CERT_SHA256")
    val signingValues =
        AndroidSigningInputs.propertyNames.associateWith { name ->
            publishingInput(name).orNull
        }

    applicationExtension.apply {
        defaultConfig {
            versionName = stableVersion.name
            versionCode = stableVersion.code
        }

        val debugBuildType = buildTypes.getByName("debug")
        buildTypes.maybeCreate("snapshot").apply {
            initWith(debugBuildType)
            applicationIdSuffix = ".snapshot"
            matchingFallbacks += "debug"
            signingConfig = null
        }

        buildTypes.getByName("release").apply {
            signingConfig = null
        }

        if (AndroidSigningInputs.propertyNames.all { !signingValues[it].isNullOrEmpty() }) {
            val releaseSigningConfig = signingConfigs.maybeCreate("meetRelease")
            releaseSigningConfig.storeFile = file(requireNotNull(signingValues["ANDROID_RELEASE_KEYSTORE_FILE"]))
            releaseSigningConfig.storePassword = signingValues["ANDROID_RELEASE_STORE_PASSWORD"]
            releaseSigningConfig.keyAlias = signingValues["ANDROID_RELEASE_KEY_ALIAS"]
            releaseSigningConfig.keyPassword = signingValues["ANDROID_RELEASE_KEY_PASSWORD"]
            buildTypes.getByName("release").signingConfig = releaseSigningConfig
        }
        if (AndroidSigningInputs.snapshotPropertyNames.all { !snapshotSigningValues[it].isNullOrEmpty() }) {
            val snapshotSigningConfig = signingConfigs.maybeCreate("meetSnapshot")
            snapshotSigningConfig.storeFile =
                file(requireNotNull(snapshotSigningValues["ANDROID_SNAPSHOT_KEYSTORE_FILE"]))
            snapshotSigningConfig.storePassword = snapshotSigningValues["ANDROID_SNAPSHOT_STORE_PASSWORD"]
            snapshotSigningConfig.keyAlias = snapshotSigningValues["ANDROID_SNAPSHOT_KEY_ALIAS"]
            snapshotSigningConfig.keyPassword = snapshotSigningValues["ANDROID_SNAPSHOT_KEY_PASSWORD"]
            buildTypes.getByName("snapshot").signingConfig = snapshotSigningConfig
        }
    }

    val validateVersion =
        tasks.register<ValidateAndroidVersionTask>("validateAndroidVersion") {
            group = "verification"
            description = "Validates the canonical Android version in root version.json."
            this.versionFile.set(versionFile)
        }

    val validateSnapshot =
        tasks.register<ValidateSnapshotPublishingInputsTask>("validateSnapshotPublishingInputs") {
            group = "verification"
            description = "Validates snapshot workflow provenance and expected signer inputs."
            this.versionFile.set(versionFile)
            runNumber.convention(snapshotRunNumber)
            runAttempt.convention(snapshotRunAttempt)
            commitSha.convention(snapshotCommitSha)
            expectedCertificateSha256.convention(snapshotExpectedCertificate)
            signingPropertyPrefix.convention("ANDROID_SNAPSHOT")
            dependsOn(validateVersion)
        }

    val validateRelease =
        tasks.register<ValidateReleasePublishingInputsTask>("validateReleasePublishingInputs") {
            group = "verification"
            description = "Validates stable TLS configuration and the release signing identity."
            this.versionFile.set(versionFile)
            this.baseUrl.convention(baseUrl)
            this.pins.convention(pins)
            expectedCertificateSha256.convention(expectedCertificate)
            dependsOn(validateVersion)
        }

    val generateReleaseNetworkConfig =
        tasks.register<GenerateReleaseNetworkSecurityConfigTask>("generateReleaseNetworkSecurityConfig") {
            group = "build"
            description = "Generates the validated release-only Android network security resource."
            this.baseUrl.convention(baseUrl)
            this.pins.convention(pins)
            outputDirectory.set(layout.buildDirectory.dir("generated/res/releaseNetworkSecurityConfig"))
        }

    tasks.register<GenerateSnapshotBuildMetadataTask>("generateSnapshotBuildMetadata") {
        group = "build"
        description = "Generates canonical metadata for the unsigned snapshot APK."
        this.versionFile.set(versionFile)
        runNumber.convention(snapshotRunNumber)
        runAttempt.convention(snapshotRunAttempt)
        commitSha.convention(snapshotCommitSha)
        expectedCertificateSha256.convention(snapshotExpectedCertificate)
        workflowName.convention(publishingInput("snapshotWorkflowName", "GITHUB_WORKFLOW").orElse("local"))
        outputFile.set(layout.buildDirectory.file("release-metadata/snapshot-build.json"))
        dependsOn(validateSnapshot)
    }

    tasks.register<GenerateReleaseBuildMetadataTask>("generateReleaseBuildMetadata") {
        group = "build"
        description = "Generates canonical metadata for stable release artifacts."
        this.versionFile.set(versionFile)
        this.baseUrl.convention(baseUrl)
        this.pins.convention(pins)
        commitSha.convention(releaseCommitSha)
        expectedCertificateSha256.convention(expectedCertificate)
        workflowName.convention(publishingInput("releaseWorkflowName", "GITHUB_WORKFLOW").orElse("local"))
        outputFile.set(layout.buildDirectory.file("release-metadata/release-build.json"))
        dependsOn(validateRelease)
    }

    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withBuildType("snapshot")) { variant ->
            val snapshot =
                runCatching {
                    SnapshotVersion.parse(
                        stableVersion = stableVersion,
                        runNumber = snapshotRunNumber.orNull,
                        runAttempt = snapshotRunAttempt.orNull,
                        commitSha = snapshotCommitSha.orNull,
                    )
                }.getOrNull()
            variant.outputs.forEach { output ->
                output.versionCode.set(snapshot?.code ?: stableVersion.code)
                output.versionName.set(snapshot?.name ?: "${stableVersion.name}-snapshot.unconfigured")
            }
        }
        onVariants(selector().withBuildType("release")) { variant ->
            variant.sources.res?.addGeneratedSourceDirectory(
                generateReleaseNetworkConfig,
                GenerateReleaseNetworkSecurityConfigTask::outputDirectory,
            )
        }
    }

    tasks.configureEach {
        when {
            name.isPackagingTaskFor("Snapshot") -> dependsOn(validateSnapshot)
            name.isPackagingTaskFor("Release") -> dependsOn(validateRelease)
        }
    }
}

private fun Project.publishingInput(
    gradlePropertyName: String,
    environmentVariableName: String = gradlePropertyName,
): Provider<String> =
    providers.gradleProperty(gradlePropertyName).orElse(providers.environmentVariable(environmentVariableName))

private fun String.isPackagingTaskFor(buildType: String): Boolean {
    val names =
        setOf(
            "assemble$buildType",
            "bundle$buildType",
            "package$buildType",
            "package${buildType}Bundle",
            "package${buildType}UniversalApk",
            "sign${buildType}Bundle",
            "install$buildType",
            "makeApkFromBundleFor$buildType",
            "zipApksFor$buildType",
            "extractApksFor$buildType",
        )
    return names.any { equals(it, ignoreCase = true) }
}

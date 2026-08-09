private val stableVersionPattern = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")
private val versionJsonPattern =
    Regex("""\s*\{\s*"version"\s*:\s*"([^"\\]*)"\s*}\s*""")

data class AndroidVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val name: String = "$major.$minor.$patch"
    val code: Int

    init {
        require(major in 0..MAX_MAJOR) {
            "Android version major must be in 0..$MAX_MAJOR (got $major)."
        }
        require(minor in 0..MAX_MINOR) {
            "Android version minor must be in 0..$MAX_MINOR (got $minor)."
        }
        require(patch in 0..MAX_PATCH) {
            "Android version patch must be in 0..$MAX_PATCH (got $patch)."
        }
        code =
            Math.addExact(
                Math.addExact(Math.multiplyExact(major, MAJOR_FACTOR), Math.multiplyExact(minor, MINOR_FACTOR)),
                patch,
            ).also {
                require(it in MIN_VERSION_CODE..MAX_VERSION_CODE) {
                    "Android versionCode must be in $MIN_VERSION_CODE..$MAX_VERSION_CODE (got $it)."
                }
            }
    }

    companion object {
        const val MAX_MAJOR = 2_099
        const val MAX_MINOR = 999
        const val MAX_PATCH = 999
        const val MIN_VERSION_CODE = 1
        const val MAX_VERSION_CODE = 2_099_999_999
        private const val MAJOR_FACTOR = 1_000_000
        private const val MINOR_FACTOR = 1_000

        fun parse(value: String): AndroidVersion {
            val match =
                requireNotNull(stableVersionPattern.matchEntire(value)) {
                    "Android version must be canonical stable SemVer MAJOR.MINOR.PATCH (got '$value')."
                }
            val components =
                match.groupValues.drop(1).map { component ->
                    component.toIntOrNull()
                        ?: throw IllegalArgumentException("Android version component is too large: '$component'.")
                }
            return AndroidVersion(components[0], components[1], components[2])
        }

        fun parseJson(json: String): AndroidVersion {
            val match =
                requireNotNull(versionJsonPattern.matchEntire(json)) {
                    "version.json must contain exactly one string field named 'version'."
                }
            return parse(match.groupValues[1])
        }
    }
}

internal object MapboxPublicToken {
    private val publicTokenPattern = Regex("pk\\.[A-Za-z0-9._-]+")

    fun safeBuildValue(value: String?): String? =
        value?.takeIf(publicTokenPattern::matches)

    fun validateReleaseValue(value: String?): String {
        require(!value.isNullOrBlank()) {
            "MAPBOX_PUBLIC_TOKEN_RELEASE must be configured for stable packaging."
        }
        require(publicTokenPattern.matches(value)) {
            "MAPBOX_PUBLIC_TOKEN_RELEASE must be a public pk. token using only letters, digits, period, underscore, or hyphen."
        }
        return value
    }
}

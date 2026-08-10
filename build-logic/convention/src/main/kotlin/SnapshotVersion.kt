private val fullCommitShaPattern = Regex("[0-9a-f]{40}")

data class SnapshotVersion(
    val stableVersion: AndroidVersion,
    val runNumber: Int,
    val runAttempt: Int,
    val commitSha: String,
) {
    val code: Int = runNumber
    val shortCommitSha: String = commitSha.take(SHORT_SHA_LENGTH)
    val name: String = "${stableVersion.name}-snapshot.$runNumber.$runAttempt+$shortCommitSha"

    init {
        require(runNumber in MIN_RUN_NUMBER..MAX_RUN_NUMBER) {
            "snapshotRunNumber must be in $MIN_RUN_NUMBER..$MAX_RUN_NUMBER (got $runNumber)."
        }
        require(runAttempt >= 1) {
            "snapshotRunAttempt must be a positive integer (got $runAttempt)."
        }
        require(fullCommitShaPattern.matches(commitSha)) {
            "snapshotCommitSha must be exactly 40 lowercase hexadecimal characters."
        }
    }

    companion object {
        const val MIN_RUN_NUMBER = 1
        const val MAX_RUN_NUMBER = 2_100_000_000
        const val SHORT_SHA_LENGTH = 12

        fun parse(
            stableVersion: AndroidVersion,
            runNumber: String?,
            runAttempt: String?,
            commitSha: String?,
        ): SnapshotVersion =
            SnapshotVersion(
                stableVersion = stableVersion,
                runNumber = runNumber.requirePositiveDecimal("snapshotRunNumber"),
                runAttempt = runAttempt.requirePositiveDecimal("snapshotRunAttempt"),
                commitSha =
                    requireNotNull(commitSha) {
                        "snapshotCommitSha is required."
                    },
            )
    }
}

private fun String?.requirePositiveDecimal(name: String): Int {
    require(!isNullOrEmpty()) { "$name is required." }
    require(matches(Regex("[1-9]\\d*"))) { "$name must be a canonical positive integer (got '$this')." }
    return toIntOrNull() ?: throw IllegalArgumentException("$name is too large.")
}

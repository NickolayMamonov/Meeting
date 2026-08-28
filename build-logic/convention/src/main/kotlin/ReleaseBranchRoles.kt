import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class ReleaseBranchRole {
    INTEGRATION,
    STABLE,
}

data class ReleaseBranchAuthority(
    val role: ReleaseBranchRole,
    val branch: String,
) {
    init {
        requireCanonicalBranch(branch)
    }

    val headRef: String = "refs/heads/$branch"
}

data class ReleaseBranchRoles(
    val integration: ReleaseBranchAuthority,
    val stable: ReleaseBranchAuthority,
) {
    init {
        require(integration.role == ReleaseBranchRole.INTEGRATION) {
            "Integration authority must have the integration role."
        }
        require(stable.role == ReleaseBranchRole.STABLE) {
            "Stable authority must have the stable role."
        }
        require(integration.branch != stable.branch) {
            "Integration and stable authorities must use different branches."
        }
    }

    fun authority(role: ReleaseBranchRole): ReleaseBranchAuthority =
        when (role) {
            ReleaseBranchRole.INTEGRATION -> integration
            ReleaseBranchRole.STABLE -> stable
        }

    companion object {
        private const val SCHEMA_VERSION = 1
        private val json =
            Json {
                ignoreUnknownKeys = false
                isLenient = false
            }

        fun parseJson(value: String): ReleaseBranchRoles {
            DuplicateJsonMemberScanner(value).scan()
            val root =
                try {
                    json.parseToJsonElement(value)
                } catch (error: SerializationException) {
                    throw IllegalArgumentException("Release branch roles must be valid JSON.", error)
                }

            val rootObject = requireObject(root, "root")
            requireExactKeys(rootObject, setOf("schemaVersion", "roles"), "root")
            val schemaVersion = requireNumber(rootObject["schemaVersion"], "schemaVersion")
            require(schemaVersion == SCHEMA_VERSION) {
                "Unsupported release branch roles schemaVersion '$schemaVersion'."
            }

            val roles = requireObject(rootObject["roles"], "roles")
            requireExactKeys(roles, setOf("integration", "stable"), "roles")
            val integration = parseAuthority(roles["integration"], ReleaseBranchRole.INTEGRATION)
            val stable = parseAuthority(roles["stable"], ReleaseBranchRole.STABLE)
            return ReleaseBranchRoles(integration, stable)
        }

        private fun parseAuthority(
            element: JsonElement?,
            role: ReleaseBranchRole,
        ): ReleaseBranchAuthority {
            val roleName = role.name.lowercase()
            val roleObject = requireObject(element, "roles.$roleName")
            requireExactKeys(roleObject, setOf("branch"), "roles.$roleName")
            val branchElement = requirePrimitive(roleObject["branch"], "roles.$roleName.branch")
            require(branchElement.isString) {
                "roles.$roleName.branch must be a string."
            }
            return ReleaseBranchAuthority(role, branchElement.content)
        }

        private fun requireObject(
            element: JsonElement?,
            path: String,
        ): JsonObject =
            element as? JsonObject
                ?: throw IllegalArgumentException("$path must be an object.")

        private fun requirePrimitive(
            element: JsonElement?,
            path: String,
        ): JsonPrimitive =
            element as? JsonPrimitive
                ?: throw IllegalArgumentException("$path must be a primitive.")

        private fun requireNumber(
            element: JsonElement?,
            path: String,
        ): Int {
            val primitive = requirePrimitive(element, path)
            require(!primitive.isString) {
                "$path must be an integer."
            }
            return primitive.content.toIntOrNull()
                ?: throw IllegalArgumentException("$path must be an integer.")
        }

        private fun requireExactKeys(
            objectValue: JsonObject,
            expected: Set<String>,
            path: String,
        ) {
            require(objectValue.keys == expected) {
                "$path must contain exactly these keys: ${expected.sorted().joinToString()}."
            }
        }
    }
}

private val canonicalBranchPattern = Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")

private fun requireCanonicalBranch(branch: String) {
    require(canonicalBranchPattern.matches(branch)) {
        "Branch must use the canonical Git-compatible branch grammar (got '$branch')."
    }
    require(!branch.startsWith("refs/heads/")) {
        "Branch must be a branch name, not a full ref (got '$branch')."
    }
    require(!branch.contains("..")) {
        "Branch must not contain '..' (got '$branch')."
    }
    require(!branch.contains("@{")) {
        "Branch must not contain '@{' (got '$branch')."
    }
    require(!branch.contains("//") && !branch.startsWith("/") && !branch.endsWith("/")) {
        "Branch must not have empty path components (got '$branch')."
    }
    require(!branch.endsWith(".")) {
        "Branch must not end with a dot (got '$branch')."
    }
    require(branch.split('/').none { it.startsWith(".") || it.endsWith(".lock") }) {
        "Branch path components must not start with '.' or end with '.lock' (got '$branch')."
    }
}

/**
 * Scans JSON syntax before kotlinx.serialization builds its tree so duplicate
 * object members cannot be collapsed by a map-backed JsonObject.
 */
private class DuplicateJsonMemberScanner(
    private val source: String,
) {
    private var index = 0

    fun scan() {
        skipWhitespace()
        scanValue()
        skipWhitespace()
        requireAtEnd("Unexpected trailing JSON content.")
    }

    private fun scanValue() {
        when (source.getOrNull(index)) {
            '{' -> scanObject()
            '[' -> scanArray()
            '"' -> scanString()
            't' -> scanLiteral("true")
            'f' -> scanLiteral("false")
            'n' -> scanLiteral("null")
            '-', in '0'..'9' -> scanNumber()
            else -> fail("Expected a JSON value.")
        }
    }

    private fun scanObject() {
        index++
        skipWhitespace()
        if (consume('}')) {
            return
        }

        val keys = mutableSetOf<String>()
        while (true) {
            requireCurrent('"', "Object member names must be strings.")
            val key = scanString()
            require(keys.add(key)) {
                "Duplicate JSON object member '$key'."
            }
            skipWhitespace()
            requireCurrent(':', "Expected ':' after object member '$key'.")
            index++
            skipWhitespace()
            scanValue()
            skipWhitespace()
            when {
                consume('}') -> return
                consume(',') -> {
                    skipWhitespace()
                    require(source.getOrNull(index) != '}' && source.getOrNull(index) != null) {
                        "Trailing commas are not valid JSON."
                    }
                }
                else -> fail("Expected ',' or '}' after object member '$key'.")
            }
        }
    }

    private fun scanArray() {
        index++
        skipWhitespace()
        if (consume(']')) {
            return
        }

        while (true) {
            scanValue()
            skipWhitespace()
            when {
                consume(']') -> return
                consume(',') -> {
                    skipWhitespace()
                    require(source.getOrNull(index) != ']' && source.getOrNull(index) != null) {
                        "Trailing commas are not valid JSON."
                    }
                }
                else -> fail("Expected ',' or ']' after array value.")
            }
        }
    }

    private fun scanString(): String {
        requireCurrent('"', "Expected a JSON string.")
        index++
        val result = StringBuilder()
        while (index < source.length) {
            when (val character = source[index++]) {
                '"' -> return result.toString()
                '\\' -> result.append(scanEscape())
                else -> {
                    require(character >= ' ') {
                        "JSON strings must not contain control characters."
                    }
                    result.append(character)
                }
            }
        }
        fail("Unterminated JSON string.")
    }

    private fun scanEscape(): String {
        require(index < source.length) {
            "Unterminated JSON escape sequence."
        }
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped.toString()
            'b' -> "\b"
            'f' -> "\u000C"
            'n' -> "\n"
            'r' -> "\r"
            't' -> "\t"
            'u' -> {
                require(index + 4 <= source.length) {
                    "Incomplete JSON unicode escape sequence."
                }
                val codeUnit =
                    source.substring(index, index + 4).toIntOrNull(16)
                        ?: fail("Invalid JSON unicode escape sequence.")
                index += 4
                codeUnit.toChar().toString()
            }
            else -> fail("Invalid JSON escape sequence '\\$escaped'.")
        }
    }

    private fun scanLiteral(literal: String) {
        require(source.regionMatches(index, literal, 0, literal.length)) {
            "Invalid JSON literal."
        }
        index += literal.length
    }

    private fun scanNumber() {
        if (consume('-')) {
            require(index < source.length) {
                "Incomplete JSON number."
            }
        }
        when {
            consume('0') -> Unit
            source.getOrNull(index) in '1'..'9' -> {
                index++
                while (source.getOrNull(index) in '0'..'9') {
                    index++
                }
            }
            else -> fail("Invalid JSON number.")
        }
        if (consume('.')) {
            require(source.getOrNull(index) in '0'..'9') {
                "JSON number fractions require digits."
            }
            while (source.getOrNull(index) in '0'..'9') {
                index++
            }
        }
        if (source.getOrNull(index) == 'e' || source.getOrNull(index) == 'E') {
            index++
            if (source.getOrNull(index) == '+' || source.getOrNull(index) == '-') {
                index++
            }
            require(source.getOrNull(index) in '0'..'9') {
                "JSON number exponents require digits."
            }
            while (source.getOrNull(index) in '0'..'9') {
                index++
            }
        }
    }

    private fun skipWhitespace() {
        while (source.getOrNull(index)?.let { it in charArrayOf(' ', '\t', '\n', '\r') } == true) {
            index++
        }
    }

    private fun consume(expected: Char): Boolean =
        source.getOrNull(index) == expected &&
            run {
                index++
                true
            }

    private fun requireCurrent(
        expected: Char,
        message: String,
    ) {
        require(source.getOrNull(index) == expected) {
            message
        }
    }

    private fun requireAtEnd(message: String) {
        require(index == source.length) {
            message
        }
    }

    private fun fail(message: String): Nothing =
        throw IllegalArgumentException("$message (at offset $index).")
}

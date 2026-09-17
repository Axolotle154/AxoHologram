package org.axostudio.axohologram.common.validation

/** Keeps hologram and group identifiers safe for both maps and YAML file names. */
object HologramId {
    private val pattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

    @JvmStatic
    fun isValid(value: String?): Boolean = value != null && pattern.matches(value)

    @JvmStatic
    fun requireValid(value: String, label: String = "Hologram id"): String {
        require(isValid(value)) { "$label must contain 1-64 letters, numbers, underscores or hyphens and cannot start with a separator." }
        return value
    }
}

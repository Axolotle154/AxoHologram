package org.axostudio.axohologram.core.hologram.visibility

import java.util.Locale

enum class VisibilityMode {
    ALL,
    PERMISSION,
    CONDITION,
    MANUAL,
    SCRIPT,
    RADIUS,
    NONE;

    companion object {
        @JvmStatic
        fun fromString(raw: String?): VisibilityMode {
            if (raw.isNullOrBlank()) return ALL
            return runCatching {
                valueOf(raw.trim().uppercase(Locale.ROOT))
            }.getOrDefault(ALL)
        }
    }
}

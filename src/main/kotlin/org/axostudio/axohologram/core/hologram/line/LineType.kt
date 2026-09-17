package org.axostudio.axohologram.core.hologram.line

import java.util.Locale

enum class LineType {
    TEXT,
    ITEM,
    BLOCK,
    COMPOSITE,
    ANIMATED_TEXT,
    UNKNOWN;

    companion object {
        @JvmStatic
        fun fromString(raw: String?): LineType {
            if (raw.isNullOrBlank()) return TEXT
            return runCatching {
                valueOf(raw.trim().uppercase(Locale.ROOT))
            }.getOrDefault(UNKNOWN)
        }
    }
}

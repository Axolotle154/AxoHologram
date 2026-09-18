package org.axostudio.axohologram.config

/** Resolves the shared prefix token used by language files before MiniMessage parses them. */
internal object MessageTemplateResolver {
    fun resolve(message: String, prefix: String): String {
        val hasPrefixToken = message.contains("<prefix>") || message.contains("%prefix%")
        val resolved = message
            .replace("<prefix>", prefix)
            .replace("%prefix%", prefix)

        // Keep custom language files consistent with the bundled files: every
        // user-facing message gets the configured prefix exactly once.
        return if (prefix.isNotBlank() && !hasPrefixToken) prefix + resolved else resolved
    }
}

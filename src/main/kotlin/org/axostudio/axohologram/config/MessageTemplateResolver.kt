package org.axostudio.axohologram.config

/** Resolves the shared prefix token used by language files before MiniMessage parses them. */
internal object MessageTemplateResolver {
    fun resolve(message: String, prefix: String): String = message
        .replace("<prefix>", prefix)
        .replace("%prefix%", prefix)
}

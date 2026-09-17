package org.axostudio.axohologram.feature.animation.text

import java.util.regex.Pattern

class TextAnimationRenderer(private val animationProvider: (String) -> TextAnimation?) {

    companion object {
        private val PAIRED_ANIM_PATTERN = Pattern.compile("(?s)<anim:([a-zA-Z0-9_-]+)>(.*?)</anim:\\1>")
        private val OPEN_ANIM_PATTERN = Pattern.compile("<anim:([a-zA-Z0-9_-]+)>")
    }

    fun renderAnimations(text: String, currentTick: Long): String {
        if (!text.contains("<anim:")) return text

        val matcher = PAIRED_ANIM_PATTERN.matcher(text)
        val sb = StringBuilder()
        while (matcher.find()) {
            val animName = matcher.group(1)
            val animation = animationProvider(animName)
            val frame = animation?.animate(matcher.group(2), null, currentTick) ?: matcher.group(0)
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(frame))
        }
        matcher.appendTail(sb)
        val pairedResult = sb.toString()
        // Accept opening-only tags from early development builds without corrupting unknown tags.
        val openMatcher = OPEN_ANIM_PATTERN.matcher(pairedResult)
        val result = StringBuffer()
        while (openMatcher.find()) {
            val animation = animationProvider(openMatcher.group(1))
            openMatcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(animation?.getCurrentFrame(currentTick) ?: openMatcher.group(0)))
        }
        openMatcher.appendTail(result)
        return result.toString()
    }
}

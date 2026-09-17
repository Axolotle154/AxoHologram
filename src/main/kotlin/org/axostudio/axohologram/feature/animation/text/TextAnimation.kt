package org.axostudio.axohologram.feature.animation.text

import org.axostudio.axohologram.api.animation.TextAnimation as ITextAnimation

interface TextAnimation : ITextAnimation {
    fun getCurrentFrame(tick: Long): String
}

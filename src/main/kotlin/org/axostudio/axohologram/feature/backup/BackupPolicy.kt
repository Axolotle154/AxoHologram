package org.axostudio.axohologram.feature.backup

data class BackupPolicy(
    val maxRetained: Int = 10,
    val includeMedia: Boolean = false
)

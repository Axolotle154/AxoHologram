package org.axostudio.axohologram.persistence.migration

import java.io.File

interface ConfigMigration {
    val fromVersion: Int
    val toVersion: Int
    fun migrate(dataFolder: File): Boolean
}

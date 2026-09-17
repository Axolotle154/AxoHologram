package org.axostudio.axohologram.persistence.migration

import java.io.File

class MigrationManager(private val dataFolder: File) {

    private val migrations = mutableListOf<ConfigMigration>()

    fun register(migration: ConfigMigration) {
        migrations.add(migration)
    }

    fun applyMigrations() {
        for (m in migrations.sortedBy { it.fromVersion }) {
            m.migrate(dataFolder)
        }
    }
}

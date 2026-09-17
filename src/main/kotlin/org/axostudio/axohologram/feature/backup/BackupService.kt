package org.axostudio.axohologram.feature.backup

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

class BackupService(
    private val dataFolder: File,
    private val backupsFolder: File = File(dataFolder, "backups"),
    var policy: BackupPolicy = BackupPolicy()
) {
    fun createBackup(): File {
        if (!backupsFolder.exists()) {
            backupsFolder.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val backupFile = File(backupsFolder, "backup-$timestamp.zip")

        ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
            addFileIfExists(zos, File(dataFolder, "config.yml"), "config.yml")
            addFileIfExists(zos, File(dataFolder, "animations.yml"), "animations.yml")
            addFileIfExists(zos, File(dataFolder, "media.yml"), "media.yml")
            addFileIfExists(zos, File(dataFolder, "visibility.yml"), "visibility.yml")
            addFileIfExists(zos, File(dataFolder, "storage.yml"), "storage.yml")
            addDirectory(zos, File(dataFolder, "holograms"), "holograms/")
            addDirectory(zos, File(dataFolder, "media"), "media/")
            addDirectory(zos, File(dataFolder, "lang"), "lang/")
        }

        pruneOldBackups()
        return backupFile
    }

    fun restoreBackup(fileName: String): Boolean {
        if (fileName.contains('/') || fileName.contains('\\') || !fileName.endsWith(".zip", true)) return false
        val source = File(backupsFolder, fileName).canonicalFile
        if (source.parentFile != backupsFolder.canonicalFile || !source.isFile) return false

        val root = dataFolder.canonicalFile
        ZipInputStream(source.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(root, entry.name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator) && target != root) {
                    throw IllegalArgumentException("Invalid backup entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return true
    }

    fun listBackups(): List<String> = backupsFolder.listFiles { file -> file.isFile && file.name.endsWith(".zip", true) }
        ?.sortedByDescending { it.lastModified() }
        ?.map { it.name }
        ?: emptyList()

    private fun addFileIfExists(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists() || !file.isFile) return
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        Files.copy(file.toPath(), zos)
        zos.closeEntry()
    }

    private fun addDirectory(zos: ZipOutputStream, directory: File, prefix: String) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return
        for (file in files) {
            val name = "$prefix${file.name}"
            if (file.isDirectory) {
                addDirectory(zos, file, "$name/")
            } else {
                addFileIfExists(zos, file, name)
            }
        }
    }

    private fun pruneOldBackups() {
        val files = backupsFolder.listFiles { f -> f.isFile && f.name.endsWith(".zip") } ?: return
        if (files.size > policy.maxRetained) {
            val sorted = files.sortedBy { it.lastModified() }
            val toDelete = sorted.take(files.size - policy.maxRetained)
            for (file in toDelete) {
                file.delete()
            }
        }
    }
}

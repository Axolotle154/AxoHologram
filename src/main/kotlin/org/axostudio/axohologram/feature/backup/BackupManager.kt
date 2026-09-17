package org.axostudio.axohologram.feature.backup

import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import java.io.File
import java.util.concurrent.CompletableFuture

class BackupManager(
    private val dataFolder: File,
    private val scheduler: TaskScheduler,
    val service: BackupService = BackupService(dataFolder)
) {
    fun performBackupAsync(): CompletableFuture<File> {
        val future = CompletableFuture<File>()
        scheduler.runAsync {
            try {
                val file = service.createBackup()
                future.complete(file)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    fun restoreBackupAsync(fileName: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        scheduler.runAsync {
            try {
                future.complete(service.restoreBackup(fileName))
            } catch (error: Exception) {
                future.completeExceptionally(error)
            }
        }
        return future
    }

    fun restoreBackupAndRun(fileName: String, afterRestore: () -> Unit): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        scheduler.runAsync {
            try {
                val restored = service.restoreBackup(fileName)
                if (!restored) {
                    future.complete(false)
                    return@runAsync
                }
                scheduler.runGlobal {
                    try {
                        afterRestore()
                        future.complete(true)
                    } catch (error: Exception) {
                        future.completeExceptionally(error)
                    }
                }
            } catch (error: Exception) {
                future.completeExceptionally(error)
            }
        }
        return future
    }
}

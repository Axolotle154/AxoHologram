package org.axostudio.axohologram.feature.importer

class ImportResult(val source: String) {

    var attempted: Int = 0
        private set
    var imported: Int = 0
        private set
    var skipped: Int = 0
        private set
    var failed: Int = 0
        private set

    private val mutableMessages = mutableListOf<String>()
    val messages: List<String>
        get() = mutableMessages.toList()

    fun markImported(message: String) {
        attempted++
        imported++
        mutableMessages.add(message)
    }

    fun markSkipped(message: String) {
        attempted++
        skipped++
        mutableMessages.add(message)
    }

    fun markFailed(message: String) {
        attempted++
        failed++
        mutableMessages.add(message)
    }
}

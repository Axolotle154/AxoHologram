package org.axostudio.axohologram.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImportResult {

    private final String source;
    private int attempted;
    private int imported;
    private int skipped;
    private int failed;
    private final List<String> messages = new ArrayList<>();

    public ImportResult(String source) {
        this.source = source;
    }

    public String source() {
        return source;
    }

    public int attempted() {
        return attempted;
    }

    public int imported() {
        return imported;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public List<String> messages() {
        return Collections.unmodifiableList(messages);
    }

    public void markImported(String message) {
        attempted++;
        imported++;
        addMessage(message);
    }

    public void markSkipped(String message) {
        attempted++;
        skipped++;
        addMessage(message);
    }

    public void markFailed(String message) {
        attempted++;
        failed++;
        addMessage(message);
    }

    public void merge(ImportResult result) {
        attempted += result.attempted;
        imported += result.imported;
        skipped += result.skipped;
        failed += result.failed;
        messages.addAll(result.messages);
    }

    public void addMessage(String message) {
        if (message != null && !message.isBlank()) {
            messages.add(message);
        }
    }
}

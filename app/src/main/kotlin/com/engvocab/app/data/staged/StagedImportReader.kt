package com.engvocab.app.data.staged

import android.content.Context
import com.engvocab.core.importer.CardDraft
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

private const val STAGED_IMPORT_FILE_NAME = "pending_import.json"

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads the "staged import" file the computer-side `:cli` tool drops via
 * `adb push ... /sdcard/Android/data/com.engvocab.app/files/pending_import.json` - the app's
 * external files directory needs no runtime permission and is reachable by adb without root.
 * This is the only way cards get bulk-imported; there is no in-app file picker.
 */
class StagedImportReader(private val context: Context) {

    private fun stagedFile(): File? = context.getExternalFilesDir(null)?.resolve(STAGED_IMPORT_FILE_NAME)

    fun read(): List<CardDraft>? {
        val file = stagedFile()?.takeIf { it.exists() } ?: return null
        return try {
            json.decodeFromString<List<CardDraft>>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        stagedFile()?.takeIf { it.exists() }?.delete()
    }
}

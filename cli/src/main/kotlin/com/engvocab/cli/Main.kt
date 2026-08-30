package com.engvocab.cli

import com.engvocab.core.importer.CardDraft
import com.engvocab.core.importer.DelimitedTextParser
import com.engvocab.core.importer.ImportSource
import com.engvocab.core.importer.KindleClippingsParser
import com.engvocab.core.model.TargetLanguage
import com.engvocab.core.sync.D1Client
import com.engvocab.core.sync.D1Credentials
import com.engvocab.core.sync.D1SyncException
import java.io.File
import kotlin.system.exitProcess

/**
 * Computer-side import tool: parses a Duocards CSV or Kindle "My Clippings.txt" export (reusing
 * the exact same parsers the app uses) and pushes the resulting cards straight into the online
 * Cloudflare D1 database. The phone app then pulls them down next time it syncs - no adb, no
 * file picker. Cards already present remotely (same front + language) are skipped.
 *
 * Usage: <duocards|kindle> <input-file> <language-code>
 * Requires env vars: CF_ACCOUNT_ID, CF_D1_DATABASE_ID, CF_API_TOKEN
 */
fun main(args: Array<String>) {
    if (args.size < 3) {
        printUsage()
        exitProcess(1)
    }

    val format = args[0].lowercase()
    val inputPath = args[1]
    val languageArg = args[2].lowercase()

    val language = TargetLanguage.entries.find { it.apiCode == languageArg }
    if (language == null) {
        System.err.println(
            "Unknown language code '$languageArg'. Use one of: ${TargetLanguage.entries.joinToString { it.apiCode }}",
        )
        exitProcess(1)
    }

    val inputFile = File(inputPath)
    if (!inputFile.exists()) {
        System.err.println("File not found: $inputPath")
        exitProcess(1)
    }
    val text = inputFile.readText()

    val drafts: List<CardDraft> = when (format) {
        "duocards" -> DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS, language)
        "kindle" -> KindleClippingsParser.parse(text, language)
        else -> {
            System.err.println("Unknown format '$format'. Use 'duocards' or 'kindle'.")
            printUsage()
            exitProcess(1)
        }
    }

    if (drafts.isEmpty()) {
        System.err.println("No entries found in $inputPath - nothing to push.")
        exitProcess(1)
    }

    val credentials = readCredentialsOrExit()
    val client = D1Client(credentials)

    println("Fetching existing ${language.displayName} words from D1 to skip duplicates...")
    val existingFronts = try {
        client.fetchFrontsByLanguage(language.apiCode)
    } catch (e: D1SyncException) {
        System.err.println("Could not reach D1: ${e.message}")
        exitProcess(1)
    }

    var inserted = 0
    var skippedDuplicates = 0
    var skippedNoTranslation = 0
    for ((index, draft) in drafts.withIndex()) {
        val key = draft.front.trim().lowercase()
        when {
            key in existingFronts -> skippedDuplicates++
            draft.back.isBlank() -> skippedNoTranslation++
            else -> {
                try {
                    client.insertWord(
                        front = draft.front,
                        back = draft.back,
                        language = language.apiCode,
                        example = draft.example,
                        source = draft.source.name,
                        sourceLabel = draft.sourceLabel,
                        knownAlready = draft.knownAlready,
                    )
                    inserted++
                } catch (e: D1SyncException) {
                    System.err.println("Failed to insert '${draft.front}': ${e.message}")
                }
            }
        }
        if ((index + 1) % 100 == 0) println("  ...${index + 1}/${drafts.size}")
    }

    println()
    println("Done: $inserted words pushed to D1 (${language.displayName}).")
    if (skippedDuplicates > 0) println("  $skippedDuplicates skipped (already in D1)")
    if (skippedNoTranslation > 0) {
        println("  $skippedNoTranslation skipped (no translation - Duocards export had no back-column value)")
    }
    println("Open EngVocab on your phone and tap \"Sync now\" on the Sync tab to pull them down.")
}

private fun readCredentialsOrExit(): D1Credentials {
    val accountId = System.getenv("CF_ACCOUNT_ID")
    val databaseId = System.getenv("CF_D1_DATABASE_ID")
    val apiToken = System.getenv("CF_API_TOKEN")
    if (accountId.isNullOrBlank() || databaseId.isNullOrBlank() || apiToken.isNullOrBlank()) {
        System.err.println(
            "Missing Cloudflare credentials. Set these environment variables first:\n" +
                "  CF_ACCOUNT_ID       your Cloudflare account ID\n" +
                "  CF_D1_DATABASE_ID   the \"engvocab\" D1 database's UUID\n" +
                "  CF_API_TOKEN        an API token with D1:Edit permission\n" +
                "See the README's \"Importing your vocabulary\" section for how to find/create these.",
        )
        exitProcess(1)
    }
    return D1Credentials(accountId, databaseId, apiToken)
}

private fun printUsage() {
    System.err.println(
        """
        Usage: <duocards|kindle> <input-file> <language-code>

        language-code: one of ${TargetLanguage.entries.joinToString { it.apiCode }}

        Requires env vars: CF_ACCOUNT_ID, CF_D1_DATABASE_ID, CF_API_TOKEN

        Examples:
          duocards words.csv en
          kindle "My Clippings.txt" de
        """.trimIndent(),
    )
}

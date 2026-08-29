package com.engvocab.cli

import com.engvocab.core.importer.CardDraft
import com.engvocab.core.importer.DelimitedTextParser
import com.engvocab.core.importer.ImportSource
import com.engvocab.core.importer.KindleClippingsParser
import com.engvocab.core.model.TargetLanguage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

/**
 * Computer-side import tool: parses a Duocards CSV or Kindle "My Clippings.txt" export
 * (reusing the exact same parsers the app uses) and writes a "staged import" JSON file.
 * Push that file onto the phone with adb and the app picks it up on its Import tab -
 * no file picker or upload step needed on the phone itself.
 *
 * Usage: <duocards|kindle> <input-file> <language-code> [output-file]
 */
private val PRETTY_JSON = Json { prettyPrint = true }

fun main(args: Array<String>) {
    if (args.size < 3) {
        printUsage()
        exitProcess(1)
    }

    val format = args[0].lowercase()
    val inputPath = args[1]
    val languageArg = args[2].lowercase()
    val outputPath = args.getOrNull(3) ?: "pending_import.json"

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
        System.err.println("No entries found in $inputPath - nothing written.")
        exitProcess(1)
    }

    File(outputPath).writeText(PRETTY_JSON.encodeToString(drafts))

    val known = drafts.count { it.knownAlready }
    val missingTranslation = drafts.count { it.back.isBlank() }
    println("Parsed ${drafts.size} cards (${language.displayName}) from $inputPath")
    if (known > 0) println("  $known already marked as known/mastered")
    if (missingTranslation > 0) println("  $missingTranslation need a translation (auto-fill will run on the phone)")
    println("Wrote $outputPath")
    println()
    println("Next steps:")
    println("  adb push \"$outputPath\" /sdcard/Android/data/com.engvocab.app/files/pending_import.json")
    println("  Then open EngVocab on the phone and go to the Import tab to review and confirm.")
}

private fun printUsage() {
    System.err.println(
        """
        Usage: <duocards|kindle> <input-file> <language-code> [output-file]

        language-code: one of ${TargetLanguage.entries.joinToString { it.apiCode }}
        output-file: defaults to ./pending_import.json

        Examples:
          duocards words.csv en
          kindle "My Clippings.txt" de output.json
        """.trimIndent(),
    )
}

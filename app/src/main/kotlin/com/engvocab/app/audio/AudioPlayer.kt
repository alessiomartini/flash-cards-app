package com.engvocab.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/**
 * Reads a card's term aloud with the device's built-in speech synthesizer. Used instead of the
 * free dictionary's recorded pronunciation clips - those cover only some words, never phrases or
 * sentences, and their links rot over time, so text-to-speech is the one path that reliably works
 * for every card.
 */
class AudioPlayer(context: Context) {
    private val appContext = context.applicationContext

    private var textToSpeech: TextToSpeech? = null
    private var ttsState = TtsState.INITIALIZING

    /**
     * A [speak] call that arrived before the synthesizer finished its (async, sometimes
     * slow-on-cold-start) init - e.g. Study auto-plays the first due card the instant its
     * ViewModel is created, which can beat TTS init by a wide margin right after launching the
     * app. Without this it's just silently dropped: TTS.speak() only works once initialized.
     */
    private var pendingSpeech: Pair<String, String>? = null

    // Both of these describe a static device condition, not a per-card failure - Study auto-plays
    // on every card transition, so without this a broken/unconfigured TTS setup would toast once
    // per card for the rest of the session instead of just informing the user once.
    private var hasWarnedUnavailable = false
    private val warnedMissingLanguages = mutableSetOf<String>()

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsState = TtsState.READY
                pendingSpeech?.let { (text, languageCode) -> speakNow(text, languageCode) }
            } else {
                // No engine could be bound to at all - happens on devices with no TTS app
                // installed/configured, distinct from a specific language's voice being missing.
                ttsState = TtsState.UNAVAILABLE
            }
            pendingSpeech = null
        }
    }

    /** Reads [text] aloud, in [languageCode] (ISO 639-1). */
    fun speak(text: String, languageCode: String) {
        stop()
        when (ttsState) {
            TtsState.READY -> speakNow(text, languageCode)
            TtsState.INITIALIZING -> pendingSpeech = text to languageCode
            TtsState.UNAVAILABLE ->
                if (!hasWarnedUnavailable) {
                    hasWarnedUnavailable = true
                    toast("No text-to-speech engine found on this device - pronunciation isn't available.")
                }
        }
    }

    private fun speakNow(text: String, languageCode: String) {
        val tts = textToSpeech ?: return
        val languageResult = tts.setLanguage(Locale.forLanguageTag(languageCode))
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (warnedMissingLanguages.add(languageCode)) {
                toast("No \"$languageCode\" voice installed for text-to-speech - check Settings > Languages & input > Text-to-speech.")
            }
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pronunciation")
    }

    private fun toast(message: String) = Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()

    fun stop() {
        textToSpeech?.stop()
        pendingSpeech = null
    }

    private enum class TtsState { INITIALIZING, READY, UNAVAILABLE }
}

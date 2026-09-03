package com.engvocab.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/**
 * Streams a single pronunciation clip at a time; starting a new one stops whatever was playing.
 * Falls back to on-device text-to-speech (see [speak]) for cards the free dictionary has no
 * recorded audio for - phrases, idioms, and full sentences never have one, and even single words
 * are missing one for some entries.
 */
class AudioPlayer(context: Context) {
    private val appContext = context.applicationContext

    private var mediaPlayer: MediaPlayer? = null
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

    fun play(url: String) {
        stop()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener { stop() }
        player.setOnErrorListener { _, _, _ ->
            stop()
            toast("Couldn't play this card's pronunciation clip - check your connection and try again.")
            true
        }
        try {
            // Some pronunciation URLs saved before the parser started normalizing them are
            // protocol-relative ("//host/…mp3") - fine in a browser, but MediaPlayer has no base
            // scheme to resolve it against and fails silently. Assume https for those.
            player.setDataSource(if (url.startsWith("//")) "https:$url" else url)
            player.prepareAsync()
        } catch (e: Exception) {
            stop()
            toast("Couldn't play this card's pronunciation clip - check your connection and try again.")
        }
    }

    /** Reads [text] aloud using the device's built-in speech synthesizer, in [languageCode] (ISO 639-1). */
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
        mediaPlayer?.release()
        mediaPlayer = null
        textToSpeech?.stop()
        pendingSpeech = null
    }

    private enum class TtsState { INITIALIZING, READY, UNAVAILABLE }
}

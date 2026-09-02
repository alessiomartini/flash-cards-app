package com.engvocab.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Streams a single pronunciation clip at a time; starting a new one stops whatever was playing.
 * Falls back to on-device text-to-speech (see [speak]) for cards the free dictionary has no
 * recorded audio for - phrases, idioms, and full sentences never have one, and even single words
 * are missing one for some entries.
 */
class AudioPlayer(context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
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
        player.setOnErrorListener { _, _, _ -> stop(); true }
        try {
            // Some pronunciation URLs saved before the parser started normalizing them are
            // protocol-relative ("//host/…mp3") - fine in a browser, but MediaPlayer has no base
            // scheme to resolve it against and fails silently. Assume https for those.
            player.setDataSource(if (url.startsWith("//")) "https:$url" else url)
            player.prepareAsync()
        } catch (e: Exception) {
            stop()
        }
    }

    /** Reads [text] aloud using the device's built-in speech synthesizer, in [languageCode] (ISO 639-1). */
    fun speak(text: String, languageCode: String) {
        stop()
        val tts = textToSpeech ?: return
        if (!isTtsReady) return
        val languageResult = tts.setLanguage(Locale.forLanguageTag(languageCode))
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pronunciation")
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        textToSpeech?.stop()
    }
}

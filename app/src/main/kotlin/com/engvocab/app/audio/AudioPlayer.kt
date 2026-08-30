package com.engvocab.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer

/** Streams a single pronunciation clip at a time; starting a new one stops whatever was playing. */
class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

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
            player.setDataSource(url)
            player.prepareAsync()
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

package com.twosmoothworkstation

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class AudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false

    fun load(uri: Uri, onPrepared: () -> Unit = {}, onError: (String) -> Unit = {}) {
        release()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setOnPreparedListener {
            prepared = true
            onPrepared()
        }
        player.setOnCompletionListener { it.seekTo(0) }
        player.setOnErrorListener { _, what, extra ->
            prepared = false
            onError("Audio playback error ($what, $extra).")
            true
        }
        try {
            player.setDataSource(appContext, uri)
            player.prepareAsync()
        } catch (exception: Exception) {
            release()
            onError(exception.message ?: "Could not open this audio file.")
        }
    }

    fun play() { if (prepared) mediaPlayer?.start() }

    fun pause() {
        if (prepared && mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
    }

    fun isPlaying(): Boolean = prepared && mediaPlayer?.isPlaying == true

    fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(safeVolume, safeVolume)
    }

    fun release() {
        prepared = false
        mediaPlayer?.setOnPreparedListener(null)
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.setOnErrorListener(null)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

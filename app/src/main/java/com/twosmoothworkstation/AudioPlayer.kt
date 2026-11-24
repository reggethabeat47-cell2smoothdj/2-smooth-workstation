package com.twosmoothworkstation

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class AudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var playbackSpeed = 1f

    fun load(uri: Uri, onPrepared: () -> Unit = {}, onError: (String) -> Unit = {}) {
        release()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setOnPreparedListener {
            prepared = true
            applyPlaybackSpeed()
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

    fun stop() {
        if (!prepared) return
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        mediaPlayer?.seekTo(0)
    }

    fun isPlaying(): Boolean = prepared && mediaPlayer?.isPlaying == true
    fun isPrepared(): Boolean = prepared
    fun durationMs(): Int = if (prepared) mediaPlayer?.duration?.coerceAtLeast(0) ?: 0 else 0
    fun currentPositionMs(): Int = if (prepared) mediaPlayer?.currentPosition?.coerceAtLeast(0) ?: 0 else 0

    fun seekTo(positionMs: Int) {
        if (!prepared) return
        mediaPlayer?.seekTo(positionMs.coerceIn(0, durationMs()))
    }

    fun seekBy(deltaMs: Int) = seekTo(currentPositionMs() + deltaMs)

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2.0f)
        if (prepared) applyPlaybackSpeed()
    }

    private fun applyPlaybackSpeed() {
        try {
            mediaPlayer?.playbackParams = MediaPlayer.PlaybackParams().setSpeed(playbackSpeed).setPitch(1.0f)
        } catch (_: IllegalStateException) { }
    }

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

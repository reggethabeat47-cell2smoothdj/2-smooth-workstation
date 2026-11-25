package com.twosmoothworkstation

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Visualizer
import android.net.Uri
import kotlin.math.sqrt

class AudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var prepared = false
    private var playbackSpeed = 1f
    @Volatile private var outputLevel = 0f

    fun load(uri: Uri, onPrepared: () -> Unit = {}, onError: (String) -> Unit = {}) {
        release()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setOnPreparedListener {
            prepared = true
            applyPlaybackSpeed()
            if (hasAudioPermission()) attachVisualizer(it)
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
    fun pause() { if (prepared && mediaPlayer?.isPlaying == true) mediaPlayer?.pause() }

    fun stop() {
        if (!prepared) return
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        mediaPlayer?.seekTo(0)
    }

    fun isPlaying(): Boolean = prepared && mediaPlayer?.isPlaying == true
    fun isPrepared(): Boolean = prepared
    fun meterLevel(): Float = outputLevel

    fun enableMeter() {
        if (prepared && hasAudioPermission()) mediaPlayer?.let(::attachVisualizer)
    }

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

    fun setScratchSpeed(speed: Float) {
        if (!prepared) return
        val safeSpeed = speed.coerceIn(0.15f, 3.0f)
        try {
            mediaPlayer?.playbackParams = PlaybackParams().setSpeed(safeSpeed).setPitch(safeSpeed)
        } catch (_: IllegalStateException) { }
        catch (_: IllegalArgumentException) { }
    }

    private fun applyPlaybackSpeed() {
        try {
            mediaPlayer?.playbackParams = PlaybackParams().setSpeed(playbackSpeed).setPitch(1.0f)
        } catch (_: IllegalStateException) { }
    }

    fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(safeVolume, safeVolume)
    }

    fun release() {
        prepared = false
        visualizer?.setDataCaptureListener(null, 0, false, false)
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        outputLevel = 0f
        mediaPlayer?.setOnPreparedListener(null)
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.setOnErrorListener(null)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun hasAudioPermission(): Boolean =
        appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun attachVisualizer(player: MediaPlayer) {
        if (visualizer != null) return
        try {
            val meter = Visualizer(player.audioSessionId)
            meter.captureSize = Visualizer.getCaptureSizeRange()[1]
            meter.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    if (waveform.isEmpty()) return
                    var sumSquares = 0.0
                    for (sample in waveform) {
                        val centered = (sample.toInt() and 0xFF) - 128
                        sumSquares += centered * centered
                    }
                    val rms = sqrt(sumSquares / waveform.size) / 128.0
                    outputLevel = (rms * 3.2).toFloat().coerceIn(0f, 1f)
                }

                override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) = Unit
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
            meter.enabled = true
            visualizer = meter
        } catch (_: RuntimeException) {
            // Some Android devices disable output visualization. Playback remains available.
        }
    }
}

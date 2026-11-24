package com.twosmoothworkstation

import android.content.Context
import android.net.Uri
import kotlin.math.sqrt

class AudioEngine(context: Context) {
    private val deckOne = AudioPlayer(context)
    private val deckTwo = AudioPlayer(context)
    private var deckOneVolume = 1f
    private var deckTwoVolume = 1f
    private var masterVolume = 1f
    private var crossfader = 0.5f
    private var deckOneBpm = 120f
    private var deckTwoBpm = 120f
    private var targetBpm = 120f
    private var beatMatchEnabled = false
    private var activeScratchDeck: Int? = null
    private var resumeAfterScratch = false

    fun loadDeck(deckNumber: Int, uri: Uri, onPrepared: () -> Unit = {}, onError: (String) -> Unit = {}) {
        playerFor(deckNumber).load(uri, onPrepared, onError)
        applyVolumes()
        applyPlaybackSpeeds()
    }

    fun play(deckNumber: Int) = playerFor(deckNumber).play()
    fun pause(deckNumber: Int) = playerFor(deckNumber).pause()
    fun stop(deckNumber: Int) = playerFor(deckNumber).stop()
    fun rewind(deckNumber: Int, amountMs: Int = 10000) = playerFor(deckNumber).seekBy(-amountMs)
    fun isPlaying(deckNumber: Int): Boolean = playerFor(deckNumber).isPlaying()
    fun isReady(deckNumber: Int): Boolean = playerFor(deckNumber).isPrepared()

    fun setDeckVolume(deckNumber: Int, volume: Float) {
        when (deckNumber) {
            1 -> deckOneVolume = volume.coerceIn(0f, 1f)
            2 -> deckTwoVolume = volume.coerceIn(0f, 1f)
            else -> throw IllegalArgumentException("Deck number must be 1 or 2.")
        }
        applyVolumes()
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        applyVolumes()
    }

    fun setCrossfader(position: Float) {
        crossfader = position.coerceIn(0f, 1f)
        applyVolumes()
    }

    fun setDeckBpm(deckNumber: Int, bpm: Float) {
        val safeBpm = bpm.coerceIn(60f, 180f)
        when (deckNumber) {
            1 -> deckOneBpm = safeBpm
            2 -> deckTwoBpm = safeBpm
            else -> throw IllegalArgumentException("Deck number must be 1 or 2.")
        }
        applyPlaybackSpeeds()
    }

    fun setTargetBpm(bpm: Float) {
        targetBpm = bpm.coerceIn(60f, 180f)
        applyPlaybackSpeeds()
    }

    fun setBeatMatchEnabled(enabled: Boolean) {
        beatMatchEnabled = enabled
        applyPlaybackSpeeds()
    }

    fun beginScratch(deckNumber: Int) {
        activeScratchDeck?.let { endScratch(it) }
        activeScratchDeck = deckNumber
        val player = playerFor(deckNumber)
        resumeAfterScratch = player.isPlaying()
        if (resumeAfterScratch) player.pause()
    }

    fun scratchBy(deckNumber: Int, deltaMs: Int) {
        if (activeScratchDeck == deckNumber && playerFor(deckNumber).isPrepared()) {
            playerFor(deckNumber).seekBy(deltaMs)
        }
    }

    fun endScratch(deckNumber: Int) {
        if (activeScratchDeck != deckNumber) return
        activeScratchDeck = null
        val shouldResume = resumeAfterScratch
        resumeAfterScratch = false
        if (shouldResume) playerFor(deckNumber).play()
    }

    fun release() {
        deckOne.release()
        deckTwo.release()
    }

    private fun playerFor(deckNumber: Int): AudioPlayer = when (deckNumber) {
        1 -> deckOne
        2 -> deckTwo
        else -> throw IllegalArgumentException("Deck number must be 1 or 2.")
    }

    private fun applyVolumes() {
        val leftGain = sqrt(1f - crossfader)
        val rightGain = sqrt(crossfader)
        deckOne.setVolume(deckOneVolume * masterVolume * leftGain)
        deckTwo.setVolume(deckTwoVolume * masterVolume * rightGain)
    }

    private fun applyPlaybackSpeeds() {
        val speedOne = if (beatMatchEnabled) targetBpm / deckOneBpm else 1f
        val speedTwo = if (beatMatchEnabled) targetBpm / deckTwoBpm else 1f
        deckOne.setPlaybackSpeed(speedOne)
        deckTwo.setPlaybackSpeed(speedTwo)
    }
}

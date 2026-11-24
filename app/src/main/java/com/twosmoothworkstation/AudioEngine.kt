package com.twosmoothworkstation

import android.content.Context
import android.net.Uri

class AudioEngine(context: Context) {
    private val deckOne = AudioPlayer(context)
    private val deckTwo = AudioPlayer(context)
    private var deckOneVolume = 1f
    private var deckTwoVolume = 1f
    private var crossfader = 0.5f

    fun loadDeck(deckNumber: Int, uri: Uri, onPrepared: () -> Unit = {}, onError: (String) -> Unit = {}) {
        playerFor(deckNumber).load(uri, onPrepared, onError)
        applyVolumes()
    }

    fun play(deckNumber: Int) = playerFor(deckNumber).play()
    fun pause(deckNumber: Int) = playerFor(deckNumber).pause()
    fun isPlaying(deckNumber: Int): Boolean = playerFor(deckNumber).isPlaying()

    fun setDeckVolume(deckNumber: Int, volume: Float) {
        when (deckNumber) {
            1 -> deckOneVolume = volume.coerceIn(0f, 1f)
            2 -> deckTwoVolume = volume.coerceIn(0f, 1f)
            else -> throw IllegalArgumentException("Deck number must be 1 or 2.")
        }
        applyVolumes()
    }

    fun setCrossfader(position: Float) {
        crossfader = position.coerceIn(0f, 1f)
        applyVolumes()
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
        deckOne.setVolume(deckOneVolume * (1f - crossfader))
        deckTwo.setVolume(deckTwoVolume * crossfader)
    }
}

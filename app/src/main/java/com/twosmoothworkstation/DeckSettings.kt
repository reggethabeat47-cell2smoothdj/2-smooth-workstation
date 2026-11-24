package com.twosmoothworkstation

data class DeckSettings(
    val volume: Float = 1.0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val vocalsMuted: Boolean = false,
    val drumsMuted: Boolean = false,
    val bassMuted: Boolean = false,
    val otherMuted: Boolean = false
) {
    fun normalized(): DeckSettings = copy(
        volume = volume.coerceIn(0f, 1f),
        bass = bass.coerceIn(-1f, 1f),
        mid = mid.coerceIn(-1f, 1f),
        treble = treble.coerceIn(-1f, 1f)
    )
}

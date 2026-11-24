package com.twosmoothworkstation

data class MixerSettings(
    val masterVolume: Float = 1.0f,
    val crossfaderPosition: Float = 0.5f,
    val masterGain: Float = 1.0f
) {
    fun normalized(): MixerSettings = copy(
        masterVolume = masterVolume.coerceIn(0f, 1f),
        crossfaderPosition = crossfaderPosition.coerceIn(0f, 1f),
        masterGain = masterGain.coerceIn(0f, 2f)
    )
}

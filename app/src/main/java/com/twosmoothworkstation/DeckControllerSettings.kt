package com.twosmoothworkstation

data class DeckControllerSettings(
    val beatMatchEnabled: Boolean = false,
    val headphoneCueEnabled: Boolean = false,
    val scratchSensitivity: Float = 1.0f
) {
    fun normalized(): DeckControllerSettings =
        copy(scratchSensitivity = scratchSensitivity.coerceIn(0.1f, 3f))
}

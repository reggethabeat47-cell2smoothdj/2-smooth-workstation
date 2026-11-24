package com.twosmoothworkstation

data class AppSettings(
    val talkBackVoiceFeedbackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val singleTouchDjModeEnabled: Boolean = true,
    val multiStemSoloEnabled: Boolean = false,
    val answerOnlyInHeadphones: Boolean = false
)

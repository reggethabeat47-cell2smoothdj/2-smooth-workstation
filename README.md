# 2 Smooth Workstation

An Android DJ app project for two-deck playback.

## Current starter

- Two audio decks with local audio-file loading
- Play and pause controls for each deck
- Per-deck volume sliders and a crossfader
- Portrait mode shows one selected deck; landscape mode shows both decks
- Dark tabletop styling with a rotating jog wheel on each deck
- Jog wheel movement seeks through the track while it is playing
- Kotlin settings models for the app, mixer, and decks
- Screen labels and controls with TalkBack descriptions

This version uses Android's built-in media player. The jog wheel seeks by touch rotation; true vinyl-style pitch and reverse scratch audio, stem separation, EQ processing, waveforms, audio-session recording, and Bluetooth headphone cueing still need a dedicated audio engine.

## Android project

Open this repository in Android Studio. The app module is in `app/`, and the Kotlin package is `com.twosmoothworkstation`.

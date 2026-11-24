package com.twosmoothworkstation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var audioEngine: AudioEngine
    private lateinit var statusOne: TextView
    private lateinit var statusTwo: TextView
    private var selectedDeck = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = AudioEngine(this)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
        }
        val title = TextView(this).apply {
            text = "2 Smooth Workstation"
            textSize = 26f
            gravity = Gravity.CENTER
            contentDescription = "2 Smooth Workstation DJ app"
        }
        root.addView(title, matchWidth())
        root.addView(deckPanel(1), matchWidth())
        root.addView(deckPanel(2), matchWidth())

        root.addView(TextView(this).apply {
            text = "Crossfader"
            textSize = 18f
        }, matchWidth())

        root.addView(SeekBar(this).apply {
            max = 100
            progress = 50
            contentDescription = "Crossfader"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    audioEngine.setCrossfader(progress / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, matchWidth())
        setContentView(root)
    }

    private fun deckPanel(deckNumber: Int): LinearLayout {
        val status = TextView(this).apply {
            text = "Deck $deckNumber: no song loaded"
            textSize = 16f
            contentDescription = text
        }
        if (deckNumber == 1) statusOne = status else statusTwo = status

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 16)
            addView(status, matchWidth())
            addView(Button(this@MainActivity).apply {
                text = "Load song to deck $deckNumber"
                setOnClickListener { openAudioPicker(deckNumber) }
            }, matchWidth())
            addView(Button(this@MainActivity).apply {
                text = "Play deck $deckNumber"
                setOnClickListener {
                    if (audioEngine.isPlaying(deckNumber)) {
                        audioEngine.pause(deckNumber)
                        text = "Play deck $deckNumber"
                    } else {
                        audioEngine.play(deckNumber)
                        text = "Pause deck $deckNumber"
                    }
                }
            }, matchWidth())
            addView(SeekBar(this@MainActivity).apply {
                max = 100
                progress = 100
                contentDescription = "Deck $deckNumber volume"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        audioEngine.setDeckVolume(deckNumber, progress / 100f)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }, matchWidth())
        }
    }

    private fun openAudioPicker(deckNumber: Int) {
        selectedDeck = deckNumber
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, REQUEST_AUDIO)
    }

    @Deprecated("Uses the platform document picker to keep this starter dependency-free.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_AUDIO || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers do not offer persistable access.
        }
        val status = if (selectedDeck == 1) statusOne else statusTwo
        status.text = "Deck $selectedDeck: loading audio…"
        status.contentDescription = status.text
        audioEngine.loadDeck(
            deckNumber = selectedDeck,
            uri = uri,
            onPrepared = {
                runOnUiThread {
                    status.text = "Deck $selectedDeck: ready to play"
                    status.contentDescription = status.text
                }
            },
            onError = { message ->
                runOnUiThread {
                    status.text = "Deck $selectedDeck: $message"
                    status.contentDescription = status.text
                }
            }
        )
    }

    override fun onDestroy() {
        audioEngine.release()
        super.onDestroy()
    }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    companion object {
        private const val REQUEST_AUDIO = 27
    }
}

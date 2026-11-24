package com.twosmoothworkstation

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var audioEngine: AudioEngine
    private lateinit var statusOne: TextView
    private lateinit var statusTwo: TextView
    private lateinit var playOne: Button
    private lateinit var playTwo: Button
    private lateinit var recordOne: Button
    private lateinit var recordTwo: Button
    private var selectedDeck = 1
    private var mediaRecorder: MediaRecorder? = null
    private var recordingUri: Uri? = null
    private var recordingDescriptor: ParcelFileDescriptor? = null

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
        root.addView(TextView(this).apply {
            text = "2 Smooth Workstation"
            textSize = 26f
            gravity = Gravity.CENTER
            contentDescription = "2 Smooth Workstation DJ app"
        }, matchWidth())
        root.addView(deckPanel(1), matchWidth())
        root.addView(deckPanel(2), matchWidth())
        addSlider(root, "Crossfader: left to right", 100, 50, "Crossfader") { audioEngine.setCrossfader(it / 100f) }
        addSlider(root, "Master volume", 100, 100, "Master volume") { audioEngine.setMasterVolume(it / 100f) }
        root.addView(CheckBox(this).apply {
            text = "Beat match"
            contentDescription = "Turn beat matching on or off"
            setOnCheckedChangeListener { _, checked -> audioEngine.setBeatMatchEnabled(checked) }
        }, matchWidth())
        addSlider(root, "Target BPM: 120", 120, 60, "Target BPM from 60 to 180") { value -> audioEngine.setTargetBpm((value + 60).toFloat()) }
        addBpmSlider(root, 1)
        addBpmSlider(root, 2)
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
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
            setPadding(0, 20, 0, 12)
            addView(status, matchWidth())
            addView(Button(this@MainActivity).apply {
                text = "Load song to deck $deckNumber"
                setOnClickListener { openAudioPicker(deckNumber) }
            }, matchWidth())
            val controls = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val playButton = Button(this@MainActivity).apply {
                text = "Play"
                contentDescription = "Play or pause deck $deckNumber"
                setOnClickListener {
                    if (audioEngine.isPlaying(deckNumber)) { audioEngine.pause(deckNumber); text = "Play" }
                    else { audioEngine.play(deckNumber); text = "Pause" }
                }
            }
            val stopButton = Button(this@MainActivity).apply {
                text = "Stop"
                contentDescription = "Stop deck $deckNumber and return to the beginning"
                setOnClickListener { audioEngine.stop(deckNumber); playButton.text = "Play" }
            }
            val rewindButton = Button(this@MainActivity).apply {
                text = "Rewind"
                contentDescription = "Rewind deck $deckNumber by ten seconds"
                setOnClickListener { audioEngine.rewind(deckNumber) }
            }
            val recordButton = Button(this@MainActivity).apply {
                text = "Record"
                contentDescription = "Start or stop recording the session using the phone microphone"
                setOnClickListener { toggleRecording() }
            }
            if (deckNumber == 1) { playOne = playButton; recordOne = recordButton } else { playTwo = playButton; recordTwo = recordButton }
            controls.addView(playButton, halfWidth())
            controls.addView(stopButton, halfWidth())
            addView(controls, matchWidth())
            val moreControls = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            moreControls.addView(rewindButton, halfWidth())
            moreControls.addView(recordButton, halfWidth())
            addView(moreControls, matchWidth())
            addView(SeekBar(this@MainActivity).apply {
                max = 100
                progress = 100
                contentDescription = "Deck $deckNumber volume"
                setOnSeekBarChangeListener(sliderListener { audioEngine.setDeckVolume(deckNumber, it / 100f) })
            }, matchWidth())
            addView(TextView(this@MainActivity).apply {
                text = "Scratch pad — slide left or right"
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(8, 22, 8, 22)
                contentDescription = "Scratch pad for deck $deckNumber. Touch and slide left or right."
                setBackgroundColor(0xFFE0E0E0.toInt())
                var lastX = 0f
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { lastX = event.x; audioEngine.beginScratch(deckNumber); true }
                        MotionEvent.ACTION_MOVE -> { audioEngine.scratchBy(deckNumber, ((event.x - lastX) * 20f).toInt()); lastX = event.x; true }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { audioEngine.endScratch(deckNumber); true }
                        else -> false
                    }
                }
            }, matchWidth())
        }
    }

    private fun addBpmSlider(parent: LinearLayout, deckNumber: Int) {
        val label = TextView(this).apply { text = "Deck $deckNumber source BPM: 120"; textSize = 16f }
        parent.addView(label, matchWidth())
        parent.addView(SeekBar(this).apply {
            max = 120
            progress = 60
            contentDescription = "Deck $deckNumber source BPM from 60 to 180"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val bpm = progress + 60
                    label.text = "Deck $deckNumber source BPM: $bpm"
                    audioEngine.setDeckBpm(deckNumber, bpm.toFloat())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, matchWidth())
    }

    private fun addSlider(parent: LinearLayout, label: String, max: Int, start: Int, description: String, onChange: (Int) -> Unit) {
        parent.addView(TextView(this).apply { text = label; textSize = 18f }, matchWidth())
        parent.addView(SeekBar(this).apply {
            this.max = max
            progress = start
            contentDescription = description
            setOnSeekBarChangeListener(sliderListener(onChange))
        }, matchWidth())
    }

    private fun sliderListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun toggleRecording() {
        if (mediaRecorder != null) { stopRecording(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
    }

    private fun startRecording() {
        var uri: Uri? = null
        var descriptor: ParcelFileDescriptor? = null
        try {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "2SmoothSession_$stamp.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/2 Smooth Workstation")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Could not create the recording file.")
            descriptor = contentResolver.openFileDescriptor(uri, "w") ?: throw IllegalStateException("Could not open the recording file.")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(descriptor.fileDescriptor)
            recorder.prepare()
            recorder.start()
            recordingUri = uri
            recordingDescriptor = descriptor
            mediaRecorder = recorder
            updateRecordButtons(true)
            Toast.makeText(this, "Recording from the phone microphone. Play through the speaker for the recording.", Toast.LENGTH_LONG).show()
        } catch (exception: Exception) {
            try { descriptor?.close() } catch (_: Exception) { }
            if (uri != null) contentResolver.delete(uri, null, null)
            Toast.makeText(this, "Recording could not start: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        val recorder = mediaRecorder ?: return
        try {
            recorder.stop()
            recordingUri?.let { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                }
            }
            Toast.makeText(this, "Session recording saved in Music, 2 Smooth Workstation.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            recordingUri?.let { contentResolver.delete(it, null, null) }
            Toast.makeText(this, "Recording was too short to save. Try recording for a few seconds.", Toast.LENGTH_LONG).show()
        } finally {
            recorder.release()
            mediaRecorder = null
            recordingDescriptor?.close()
            recordingDescriptor = null
            recordingUri = null
            updateRecordButtons(false)
        }
    }

    private fun updateRecordButtons(recording: Boolean) {
        val label = if (recording) "Stop recording" else "Record"
        if (::recordOne.isInitialized) recordOne.text = label
        if (::recordTwo.isInitialized) recordTwo.text = label
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecording()
    }

    private fun openAudioPicker(deckNumber: Int) {
        selectedDeck = deckNumber
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*" }, REQUEST_AUDIO)
    }

    @Deprecated("Uses the platform document picker to keep this starter dependency-free.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_AUDIO || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
        val status = if (selectedDeck == 1) statusOne else statusTwo
        status.text = "Deck $selectedDeck: loading audio…"
        status.contentDescription = status.text
        audioEngine.loadDeck(selectedDeck, uri, onPrepared = {
            runOnUiThread { status.text = "Deck $selectedDeck: ready to play"; status.contentDescription = status.text }
        }, onError = { message -> runOnUiThread { status.text = "Deck $selectedDeck: $message"; status.contentDescription = status.text } })
    }

    override fun onDestroy() {
        mediaRecorder?.let { try { it.stop() } catch (_: Exception) { }; it.release() }
        recordingDescriptor?.close()
        audioEngine.release()
        super.onDestroy()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun halfWidth() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    companion object {
        private const val REQUEST_AUDIO = 27
        private const val REQUEST_RECORD_AUDIO = 28
    }
}

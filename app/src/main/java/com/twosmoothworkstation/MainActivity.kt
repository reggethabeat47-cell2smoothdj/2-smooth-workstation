package com.twosmoothworkstation

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    private var deckOneStatus = "Deck 1: no song loaded"
    private var deckTwoStatus = "Deck 2: no song loaded"
    private var beatMatchEnabled = false
    private var crossfaderPosition = 50
    private var masterVolumePosition = 100
    private var deckOneVolumePosition = 100
    private var deckTwoVolumePosition = 100
    private var targetBpmValue = 120
    private var deckOneBpmValue = 120
    private var deckTwoBpmValue = 120
    private var mediaRecorder: MediaRecorder? = null
    private var recordingUri: Uri? = null
    private var recordingDescriptor: ParcelFileDescriptor? = null
    private val meterViews = mutableMapOf<Int, LevelMeterView>()
    private val meterHandler = Handler(Looper.getMainLooper())
    private val meterRefresh = object : Runnable {
        override fun run() {
            for ((deck, view) in meterViews) view.setLevel(audioEngine.meterLevel(deck))
            meterHandler.postDelayed(this, 60L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = AudioEngine(this); meterHandler.post(meterRefresh)
        buildScreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildScreen()
    }

    private fun buildScreen() {
        meterViews.clear()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16, 20, 16, 20)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "2 Smooth Workstation"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "2 Smooth Workstation DJ app"
        }, matchWidth().apply { bottomMargin = 12 })

        if (landscape) {
            val decks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            decks.addView(deckPanel(1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })
            decks.addView(deckPanel(2), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 })
            root.addView(decks, matchWidth())
        } else {
            val selectors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (deck in 1..2) {
                selectors.addView(Button(this).apply {
                    text = "DECK $deck"
                    contentDescription = "Show deck $deck"
                    setOnClickListener { selectedDeck = deck; buildScreen() }
                    styleButton(this, if (selectedDeck == deck) 0xFFD21F35.toInt() else 0xFF282828.toInt())
                }, halfWidth())
            }
            root.addView(selectors, matchWidth())
            root.addView(deckPanel(selectedDeck), matchWidth())
        }
        addSlider(root, "Crossfader: left to right", 100, crossfaderPosition, "Crossfader") {
            crossfaderPosition = it
            audioEngine.setCrossfader(it / 100f)
        }
        addSlider(root, "Master volume", 100, masterVolumePosition, "Master volume") {
            masterVolumePosition = it
            audioEngine.setMasterVolume(it / 100f)
        }
        root.addView(CheckBox(this).apply {
            text = "Beat match"
            isChecked = beatMatchEnabled
            setTextColor(Color.WHITE)
            contentDescription = "Turn beat matching on or off"
            setOnCheckedChangeListener { _, checked -> beatMatchEnabled = checked; audioEngine.setBeatMatchEnabled(checked) }
        }, matchWidth())
        val targetBpmLabel = TextView(this).apply {
            text = "Target BPM: $targetBpmValue"
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        root.addView(targetBpmLabel, matchWidth())
        root.addView(SeekBar(this).apply {
            max = 120
            progress = targetBpmValue - 60
            contentDescription = "Target BPM from 60 to 180"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    targetBpmValue = progress + 60
                    targetBpmLabel.text = "Target BPM: $targetBpmValue"
                    audioEngine.setTargetBpm(targetBpmValue.toFloat())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, matchWidth())
        if (landscape) {
            val bpmControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val bpmOne = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val bpmTwo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            addBpmSlider(bpmOne, 1)
            addBpmSlider(bpmTwo, 2)
            bpmControls.addView(bpmOne, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            bpmControls.addView(bpmTwo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(bpmControls, matchWidth())
        } else {
            addBpmSlider(root, selectedDeck)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
            addView(root)
        })
    }

    private fun deckPanel(deckNumber: Int): LinearLayout {
        val status = TextView(this).apply {
            text = if (deckNumber == 1) deckOneStatus else deckTwoStatus
            textSize = 16f
            setTextColor(Color.WHITE)
            contentDescription = text
        }
        if (deckNumber == 1) statusOne = status else statusTwo = status
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF191919.toInt())
                cornerRadius = 18f
                setStroke(1, 0xFF353535.toInt())
            }
            addView(status, matchWidth())
            addView(Button(this@MainActivity).apply {
                text = "Load song to deck $deckNumber"
                styleButton(this, 0xFF343434.toInt())
                setOnClickListener { openAudioPicker(deckNumber) }
            }, matchWidth().apply { topMargin = 10 })
            val controls = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val playButton = Button(this@MainActivity).apply {
                text = if (audioEngine.isPlaying(deckNumber)) "Pause" else "Play"
                contentDescription = "Play or pause deck $deckNumber"
                styleButton(this, 0xFFD21F35.toInt())
                setOnClickListener {
                    if (audioEngine.isPlaying(deckNumber)) { audioEngine.pause(deckNumber); text = "Play" }
                    else { audioEngine.play(deckNumber); text = "Pause" }
                }
            }
            val stopButton = Button(this@MainActivity).apply {
                text = "Stop"
                contentDescription = "Stop deck $deckNumber and return to the beginning"
                styleButton(this, 0xFF343434.toInt())
                setOnClickListener { audioEngine.stop(deckNumber); playButton.text = "Play" }
            }
            val rewindButton = Button(this@MainActivity).apply {
                text = "Rewind"
                contentDescription = "Rewind deck $deckNumber by ten seconds"
                styleButton(this, 0xFF343434.toInt())
                setOnClickListener { audioEngine.rewind(deckNumber) }
            }
            val recordButton = Button(this@MainActivity).apply {
                text = if (mediaRecorder == null) "Record" else "Stop recording"
                contentDescription = "Start or stop recording the session using the phone microphone"
                styleButton(this, 0xFF343434.toInt())
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
            addView(TextView(this@MainActivity).apply {
                text = "CHANNEL LEVEL"
                textSize = 12f
                setTextColor(0xFFB8B8B8.toInt())
                gravity = Gravity.START
            }, matchWidth().apply { topMargin = 10 })
                        val meter = LevelMeterView(this@MainActivity, deckNumber)
            meterViews[deckNumber] = meter
            addView(meter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (24f * resources.displayMetrics.density).toInt()))
            addView(Button(this@MainActivity).apply {
                text = "Enable live meters"
                contentDescription = "Enable live audio output meters for deck $deckNumber"
                styleButton(this, 0xFF343434.toInt())
                setOnClickListener { if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { audioEngine.enableMeters(); Toast.makeText(this@MainActivity, "Live audio meters enabled.", Toast.LENGTH_SHORT).show() } else { Toast.makeText(this@MainActivity, "Android needs audio permission to show live output levels.", Toast.LENGTH_LONG).show(); requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_METER_PERMISSION) } }
            }, matchWidth())

            addView(SeekBar(this@MainActivity).apply {
                max = 100
                progress = if (deckNumber == 1) deckOneVolumePosition else deckTwoVolumePosition
                contentDescription = "Deck $deckNumber channel volume, from zero to one hundred percent"
                setOnSeekBarChangeListener(sliderListener {
                    if (deckNumber == 1) deckOneVolumePosition = it else deckTwoVolumePosition = it
                    audioEngine.setDeckVolume(deckNumber, it / 100f)
                })
            }, matchWidth())
            addView(JogWheelView(this@MainActivity, deckNumber, audioEngine),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 210 else 250).apply { topMargin = 12 })
        }
    }

    private fun addBpmSlider(parent: LinearLayout, deckNumber: Int) {
        val initialBpm = if (deckNumber == 1) deckOneBpmValue else deckTwoBpmValue
        val label = TextView(this).apply { text = "Deck $deckNumber source BPM: $initialBpm"; textSize = 16f; setTextColor(Color.WHITE) }
        parent.addView(label, matchWidth())
        parent.addView(SeekBar(this).apply {
            max = 120
            progress = initialBpm - 60
            contentDescription = "Deck $deckNumber source BPM from 60 to 180"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val bpm = progress + 60
                    label.text = "Deck $deckNumber source BPM: $bpm"
                    if (deckNumber == 1) deckOneBpmValue = bpm else deckTwoBpmValue = bpm
                    audioEngine.setDeckBpm(deckNumber, bpm.toFloat())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, matchWidth())
    }

    private fun addSlider(parent: LinearLayout, label: String, max: Int, start: Int, description: String, onChange: (Int) -> Unit) {
        parent.addView(TextView(this).apply { text = label; textSize = 16f; setTextColor(Color.WHITE) }, matchWidth())
        parent.addView(SeekBar(this).apply {
            this.max = max
            progress = start
            contentDescription = description
            setOnSeekBarChangeListener(sliderListener(onChange))
        }, matchWidth())
    }

    private fun styleButton(button: Button, color: Int) {
        button.setTextColor(Color.WHITE)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        button.minHeight = (48f * resources.displayMetrics.density).toInt()
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
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { audioEngine.enableMeters(); if (requestCode == REQUEST_RECORD_AUDIO) startRecording() }
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
        val loadingText = "Deck $selectedDeck: loading audio…"
        if (selectedDeck == 1) deckOneStatus = loadingText else deckTwoStatus = loadingText
        status.text = loadingText
        status.contentDescription = loadingText
        audioEngine.loadDeck(selectedDeck, uri, onPrepared = {
            runOnUiThread {
                val readyText = "Deck $selectedDeck: ready to play"
                if (selectedDeck == 1) deckOneStatus = readyText else deckTwoStatus = readyText
                status.text = readyText
                status.contentDescription = readyText
            }
        }, onError = { message -> runOnUiThread {
            val errorText = "Deck $selectedDeck: $message"
            if (selectedDeck == 1) deckOneStatus = errorText else deckTwoStatus = errorText
            status.text = errorText
            status.contentDescription = errorText
        } })
    }

    override fun onDestroy() { meterHandler.removeCallbacks(meterRefresh);
        mediaRecorder?.let { try { it.stop() } catch (_: Exception) { }; it.release() }
        recordingDescriptor?.close()
        audioEngine.release()
        super.onDestroy()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun halfWidth() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    companion object {
        private const val REQUEST_AUDIO = 27
        private const val REQUEST_RECORD_AUDIO = 28; private const val REQUEST_METER_PERMISSION = 29
    }
}

private class LevelMeterView(context: android.content.Context, private val deckNumber: Int) : View(context) { private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private var displayedLevel = 0f; init { contentDescription = "Deck $deckNumber live audio level meter"; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }; fun setLevel(level: Float) { val target = level.coerceIn(0f, 1f); displayedLevel = if (target > displayedLevel) displayedLevel + (target - displayedLevel) * 0.72f else maxOf(target, displayedLevel - 0.035f); invalidate() }; override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val segments = 24; val gap = 3f * resources.displayMetrics.density; val segmentWidth = (width - gap * (segments - 1)) / segments; val top = height * 0.18f; val bottom = height * 0.82f; val litCount = (displayedLevel * segments).toInt(); for (index in 0 until segments) { paint.color = when { index >= litCount -> 0xFF303030.toInt(); index >= segments * 0.9f -> 0xFFE53935.toInt(); index >= segments * 0.72f -> 0xFFFFC107.toInt(); else -> 0xFF55D66B.toInt() }; val left = index * (segmentWidth + gap); canvas.drawRoundRect(left, top, left + segmentWidth, bottom, 3f, 3f, paint) } } } private class JogWheelView(
    context: android.content.Context,
    private val deckNumber: Int,
    private val audioEngine: AudioEngine
) : View(context) {
    private val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101010.toInt() }
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF454545.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * resources.displayMetrics.density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD21F35.toInt() }
    private var lastAngle = 0f; private var lastMoveTimeMs = 0L
    private var rotation = 0f
    private var touching = false

    init {
        contentDescription = "Deck $deckNumber jog wheel. Touch and rotate the wheel to scratch or seek through the song."
        isFocusable = true
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f - 8f * resources.displayMetrics.density).coerceAtLeast(20f)
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF262626.toInt() })
        canvas.drawCircle(cx, cy, radius * 0.94f, vinylPaint)
        for (ring in 1..6) canvas.drawCircle(cx, cy, radius * (0.42f + ring * 0.075f), groovePaint)

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        val mark = RectF(cx - radius * 0.88f, cy - radius * 0.88f, cx + radius * 0.88f, cy + radius * 0.88f)
        canvas.drawArc(mark, 264f, 18f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF2D0.toInt(); strokeWidth = 5f * resources.displayMetrics.density; style = Paint.Style.STROKE })
        canvas.restore()

        canvas.drawCircle(cx, cy, radius * 0.34f, accentPaint)
        labelPaint.textSize = 14f * resources.displayMetrics.scaledDensity
        canvas.drawText("2 SMOOTH", cx, cy - 2f, labelPaint)
        labelPaint.textSize = 11f * resources.displayMetrics.scaledDensity
        canvas.drawText(if (touching) "SCRATCHING" else "JOG / SCRATCH", cx, cy + 20f * resources.displayMetrics.density, labelPaint)
        labelPaint.color = 0xFFB8B8B8.toInt()
        labelPaint.textSize = 11f * resources.displayMetrics.scaledDensity
        canvas.drawText("DECK $deckNumber", cx, height - 4f * resources.displayMetrics.density, labelPaint)
        labelPaint.color = Color.WHITE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val angle = Math.toDegrees(kotlin.math.atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())).toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastAngle = angle; lastMoveTimeMs = event.eventTime
                touching = true
                audioEngine.beginScratch(deckNumber)
                performClick()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var delta = angle - lastAngle
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                rotation = (rotation + delta) % 360f
                val elapsedMs = (event.eventTime - lastMoveTimeMs).coerceAtLeast(1L)
                val angularSpeed = kotlin.math.abs(delta) * 1000f / elapsedMs
                val playbackSpeed = (angularSpeed / 180f).coerceIn(0.15f, 3f)
                audioEngine.scratchBy(deckNumber, (delta / 360f * 1800f).toInt(), playbackSpeed)
                lastAngle = angle; lastMoveTimeMs = event.eventTime
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                audioEngine.endScratch(deckNumber)
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

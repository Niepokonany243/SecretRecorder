package com.example.systemservice

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AudioPlayerActivity : SecretRecorderBaseActivity() {
    companion object {
        private const val TAG = "AudioPlayerActivity"
        private const val SEEK_UPDATE_INTERVAL = 100L // Update seek bar every 100ms
    }

    // UI components
    private lateinit var backButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var audioTitle: TextView
    private lateinit var audioIcon: ImageView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: Slider
    private lateinit var playPauseButton: FloatingActionButton
    private lateinit var rewindButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var audioDetails: TextView

    // Media player
    private var mediaPlayer: MediaPlayer? = null
    private var audioUri: Uri? = null
    private var audioFile: File? = null
    private var isPlaying = false
    private var audioDuration = 0
    private var seekUpdateHandler = Handler(Looper.getMainLooper())
    private var seekUpdateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        // Initialize UI components
        initializeViews()

        // Get audio file from intent
        val audioPath = intent.getStringExtra("audioPath")
        if (audioPath != null) {
            audioFile = File(audioPath)
            setupAudioPlayer()
        } else {
            Log.e(TAG, "No audio file path provided")
            finish()
        }
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        menuButton = findViewById(R.id.menuButton)
        audioTitle = findViewById(R.id.audioTitle)
        audioIcon = findViewById(R.id.audioIcon)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        seekBar = findViewById(R.id.seekBar)
        playPauseButton = findViewById(R.id.playPauseButton)
        rewindButton = findViewById(R.id.rewindButton)
        forwardButton = findViewById(R.id.forwardButton)
        audioDetails = findViewById(R.id.audioDetails)

        // Set click listeners
        backButton.setOnClickListener { onBackPressed() }
        playPauseButton.setOnClickListener { togglePlayPause() }
        rewindButton.setOnClickListener { seekBackward() }
        forwardButton.setOnClickListener { seekForward() }
        menuButton.setOnClickListener { showOptionsMenu() }

        // Set up seek bar listener
        seekBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                mediaPlayer?.seekTo(value.toInt())
                updateTimeDisplay()
            }
        }
    }

    private fun setupAudioPlayer() {
        audioFile?.let { file ->
            try {
                // Create content URI using FileProvider
                audioUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )

                // Set audio title
                audioTitle.text = file.name.substringBeforeLast(".")

                // Set audio details
                val fileSize = formatFileSize(file.length())
                val recordDate = formatDate(file.lastModified())
                audioDetails.text = "Recorded on $recordDate • $fileSize"

                // Initialize media player
                setupMediaPlayer()
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up audio player: ${e.message}")
                finish()
            }
        }
    }

    private fun setupMediaPlayer() {
        try {
            // Release any existing media player
            releaseMediaPlayer()

            // Create new media player
            mediaPlayer = MediaPlayer()
            
            // Set up media player
            mediaPlayer?.let { player ->
                player.setDataSource(applicationContext, audioUri!!)
                
                player.setOnPreparedListener { mp ->
                    // Get duration
                    audioDuration = mp.duration
                    seekBar.valueTo = mp.duration.toFloat()
                    totalTime.text = formatDuration(mp.duration.toLong())

                    // Start playback
                    startPlayback()

                    // Start seek bar updates
                    startSeekBarUpdates()
                }
                
                player.setOnCompletionListener {
                    // Reset to paused state when playback completes
                    handlePlaybackCompletion()
                }
                
                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Media player error: what=$what, extra=$extra")
                    false
                }
                
                player.prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing media player: ${e.message}")
        }
    }

    private fun startPlayback() {
        mediaPlayer?.let {
            it.start()
            isPlaying = true
            updatePlayPauseButton()
        }
    }

    private fun handlePlaybackCompletion() {
        isPlaying = false
        updatePlayPauseButton()
        seekBar.value = 0f
        updateTimeDisplay()
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                updatePlayPauseButton()
            }
        }
    }

    private fun resumePlayback() {
        mediaPlayer?.let {
            it.start()
            isPlaying = true
            updatePlayPauseButton()
        }
    }

    private fun seekBackward() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition - 10000).coerceAtLeast(0)
            it.seekTo(newPosition)
            seekBar.value = newPosition.toFloat()
            updateTimeDisplay()
        }
    }

    private fun seekForward() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition + 10000).coerceAtMost(audioDuration)
            it.seekTo(newPosition)
            seekBar.value = newPosition.toFloat()
            updateTimeDisplay()
        }
    }

    private fun updatePlayPauseButton() {
        playPauseButton.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun startSeekBarUpdates() {
        seekUpdateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        val currentPos = it.currentPosition
                        seekBar.value = currentPos.toFloat()
                        updateTimeDisplay()
                    }
                }
                seekUpdateHandler.postDelayed(this, SEEK_UPDATE_INTERVAL)
            }
        }
        seekUpdateHandler.post(seekUpdateRunnable!!)
    }

    private fun updateTimeDisplay() {
        mediaPlayer?.let {
            val currentPos = it.currentPosition
            currentTime.text = formatDuration(currentPos.toLong())
        }
    }

    private fun showOptionsMenu() {
        // Implement options menu (share, delete, etc.)
        // This can be expanded later
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            else -> "${sizeInBytes / (1024 * 1024)} MB"
        }
    }

    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        stopSeekBarUpdates()
    }

    private fun stopSeekBarUpdates() {
        seekUpdateRunnable?.let {
            seekUpdateHandler.removeCallbacks(it)
        }
        seekUpdateRunnable = null
    }

    override fun onPause() {
        super.onPause()
        pausePlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }
}

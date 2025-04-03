package com.example.systemservice

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.FrameLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.google.android.material.slider.Slider
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout

class RecordingsBrowserActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RecordingsBrowser"

        // Helper method to extract FPS from filename pattern
        fun extractFpsFromFileName(filename: String): Int {
            try {
                // Try to find patterns like width_height_fps or similar
                val parts = filename.split("_")
                if (parts.size >= 3) {
                    // Try the last part before extension
                    val lastPart = parts.last().split(".").first()
                    if (lastPart.endsWith("fps")) {
                        return lastPart.removeSuffix("fps").toIntOrNull() ?: 0
                    }

                    // Try second to last part
                    if (parts.size >= 4) {
                        val secondToLast = parts[parts.size - 2]
                        if (secondToLast.endsWith("fps")) {
                            return secondToLast.removeSuffix("fps").toIntOrNull() ?: 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting FPS from filename: ${e.message}")
            }
            return 0
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: RecordingsAdapter
    private lateinit var previewContainer: ConstraintLayout
    private lateinit var videoPreview: ZoomableTextureVideoView
    private lateinit var previewControls: ConstraintLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // New video player UI elements
    private lateinit var videoSeekBar: Slider
    private lateinit var durationText: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var videoDetailsPanel: View
    private lateinit var videoResolutionText: TextView
    private lateinit var videoFpsText: TextView
    private lateinit var videoBitrateText: TextView
    private lateinit var videoCodecText: TextView
    private lateinit var videoSizeText: TextView

    private val recordings = mutableListOf<RecordingFile>()
    private var allRecordings = mutableListOf<RecordingFile>()
    private val thumbnailCache = mutableMapOf<String, Bitmap>()
    private val thumbnailExecutor = Executors.newFixedThreadPool(2)

    // Video playback state
    private var currentRecording: RecordingFile? = null
    private var seekUpdateHandler = Handler(Looper.getMainLooper())
    private var seekUpdateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings_browser)

        // Set up views
        recyclerView = findViewById(R.id.recordingsRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        previewContainer = findViewById(R.id.previewContainer)
        videoPreview = findViewById(R.id.videoPreview)
        previewControls = findViewById(R.id.previewControls)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        // Set up new video player UI elements
        videoSeekBar = findViewById(R.id.videoSeekBar)
        durationText = findViewById(R.id.durationText)
        playPauseButton = findViewById(R.id.playPauseButton)
        videoDetailsPanel = findViewById(R.id.videoDetailsPanel)
        videoResolutionText = findViewById(R.id.videoResolutionText)
        videoFpsText = findViewById(R.id.videoFpsText)
        videoBitrateText = findViewById(R.id.videoBitrateText)
        videoCodecText = findViewById(R.id.videoCodecText)
        videoSizeText = findViewById(R.id.videoSizeText)

        // Set up preview action buttons
        val shareButton = findViewById<ImageButton>(R.id.shareButton)
        val deleteButton = findViewById<ImageButton>(R.id.deleteButton)

        shareButton.setOnClickListener {
            // Find the currently previewed recording
            currentRecording?.let { recording ->
                shareRecording(recording)
            }
        }

        deleteButton.setOnClickListener {
            // Find the currently previewed recording
            currentRecording?.let { recording ->
                hideVideoPreview()
                confirmDelete(recording)
            }
        }

        // Initialize preview container as hidden
        previewContainer.visibility = View.GONE
        videoDetailsPanel.visibility = View.GONE

        // Configure RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecordingsAdapter(
            recordings,
            onItemClick = { recording ->
                if (recording.isVideo) {
                    showVideoPreview(recording)
                } else {
                    playRecording(recording)
                }
            },
            onMenuClick = { view, recording -> showRecordingOptions(view, recording) },
            onThumbnailLoaded = { recording, bitmap ->
                if (recording.isVideo && bitmap != null) {
                    thumbnailCache[recording.file.absolutePath] = bitmap
                }
            }
        )
        recyclerView.adapter = adapter

        // Set up video player controls
        setupVideoPlayerControls()

        // Set up SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            loadRecordings()
            swipeRefreshLayout.isRefreshing = false
        }

        // Load recordings
        loadRecordings()

        // Setup search filtering
        val searchInput = findViewById<EditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterRecordings() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Setup tab selection listener for categories
        val tabLayout = findViewById<TabLayout>(R.id.recordingTypeTabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { filterRecordings() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) { filterRecordings() }
        })
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when coming back to this screen
        loadRecordings()
    }

    override fun onBackPressed() {
        // If preview is visible, close it instead of exiting activity
        if (previewContainer.visibility == View.VISIBLE) {
            hideVideoPreview()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadRecordings() {
        allRecordings.clear()
        val recordingsDir = getOutputDirectory()

        if (recordingsDir.exists()) {
            val files = recordingsDir.listFiles()?.filter {
                it.name.endsWith(".mp4") || it.name.endsWith(".m4a")
            }

            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    val isVideo = file.name.endsWith(".mp4")
                    val duration = if (isVideo) getVideoDuration(file) else getAudioDuration(file)

                    allRecordings.add(
                        RecordingFile(
                            file.name,
                            file.length(),
                            file.lastModified(),
                            file,
                            isVideo,
                            duration
                        )
                    )
                }
                allRecordings.sortByDescending { it.timestamp }
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            } else {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        } else {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        recordings.clear()
        recordings.addAll(allRecordings)
        adapter.notifyDataSetChanged()
    }

    private fun getVideoDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video duration: ${e.message}")
            0
        }
    }

    private fun getAudioDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            0
        }
    }

    private fun showVideoPreview(recording: RecordingFile) {
        try {
            currentRecording = recording

            // Create content URI using FileProvider
            val fileUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                recording.file
            )

            // Reset zoom level
            videoPreview.setZoom(1.0f, false)

            // Show loading indicator
            showLoadingState(true)

            // Configure video preview error handling
            videoPreview.setOnErrorListener { error ->
                showLoadingState(false)
                Toast.makeText(this, "Error playing video: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Video player error: ${error.message}", error)
            }

            // Set up video view
            videoPreview.setVideoUri(fileUri)

            // Show preview container
            previewContainer.visibility = View.VISIBLE

            // Hide details panel by default
            videoDetailsPanel.visibility = View.GONE

            // Update file size
            videoSizeText.text = "Size: ${formatFileSize(recording.size)}"
        } catch (e: Exception) {
            showLoadingState(false)
            Log.e(TAG, "Error showing video preview: ${e.message}", e)
            Toast.makeText(this, "Error playing video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoadingState(isLoading: Boolean) {
        // Find or add loading indicator to layout
        val loadingIndicator = findViewById<View>(R.id.videoLoadingIndicator)
        loadingIndicator?.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun hideVideoPreview() {
        // Stop playback and release resources
        videoPreview.release()
        stopSeekBarUpdate()

        // Hide preview container
        previewContainer.visibility = View.GONE
        currentRecording = null
    }

    private fun playRecording(recording: RecordingFile) {
        try {
            if (recording.isVideo) {
                // For video files, use the external player as before
                // Create content URI using FileProvider
                val fileUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    recording.file
                )

                // Create intent to play video file
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Check if there's an app that can handle this intent
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this,
                        "No app found to play video",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // For audio files, use our built-in audio player
                val intent = Intent(this, AudioPlayerActivity::class.java).apply {
                    putExtra("audioPath", recording.file.absolutePath)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing file: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error playing recording: ${e.message}", e)
        }
    }

    private fun showRecordingOptions(view: View, recording: RecordingFile) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.recording_options_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    confirmDelete(recording)
                    true
                }
                R.id.action_share -> {
                    shareRecording(recording)
                    true
                }
                R.id.action_rename -> {
                    showRenameDialog(recording)
                    true
                }
                R.id.action_details -> {
                    if (recording.isVideo && currentRecording == recording && previewContainer.visibility == View.VISIBLE) {
                        // If we're already viewing this video, show the details panel
                        videoDetailsPanel.visibility = View.VISIBLE
                    } else {
                        showRecordingDetails(recording)
                    }
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun confirmDelete(recording: RecordingFile) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to delete this recording?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecording(recording)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecording(recording: RecordingFile) {
        try {
            if (recording.file.delete()) {
                allRecordings.remove(recording)
                adapter.notifyDataSetChanged()

                // Show empty view if no recordings left
                if (allRecordings.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }

                Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to delete recording", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error deleting recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareRecording(recording: RecordingFile) {
        try {
            val fileUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                recording.file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (recording.isVideo) "video/mp4" else "audio/mp4a-latm"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share recording via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(recording: RecordingFile) {
        val editText = EditText(this).apply {
            setText(recording.name)
            setPadding(32, 16, 32, 16)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rename Recording")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    renameRecording(recording, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameRecording(recording: RecordingFile, newName: String) {
        try {
            // Ensure the extension is preserved
            val extension = if (recording.isVideo) ".mp4" else ".m4a"
            val finalName = if (newName.endsWith(extension)) newName else "$newName$extension"

            // Create the destination file
            val destFile = File(recording.file.parent, finalName)

            // Rename the file
            if (recording.file.renameTo(destFile)) {
                // Update the recording object
                val index = allRecordings.indexOf(recording)
                if (index >= 0) {
                    allRecordings[index] = recording.copy(
                        name = finalName,
                        file = destFile
                    )
                    adapter.notifyItemChanged(index)
                }
                Toast.makeText(this, "Recording renamed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to rename recording", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error renaming recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecordingDetails(recording: RecordingFile) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val date = Date(recording.timestamp)
        val formattedDate = dateFormat.format(date)

        val fileSize = formatFileSize(recording.size)
        val duration = formatDuration(recording.duration)
        val type = if (recording.isVideo) "Video" else "Audio"

        val detailsBuilder = StringBuilder()
        detailsBuilder.append("File: ${recording.name}\n")
        detailsBuilder.append("Type: $type\n")
        if (recording.isVideo) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(recording.file.absolutePath)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (width != null && height != null) {
                    val qualityName = when {
                        height.toInt() >= 2160 -> "4K UHD (2160p)"
                        height.toInt() >= 1080 -> "Full HD (1080p)"
                        height.toInt() >= 720 -> "HD (720p)"
                        height.toInt() >= 480 -> "SD (480p)"
                        else -> "${height}p"
                    }
                    detailsBuilder.append("Resolution: ${width}x${height} ($qualityName)\n")
                } else {
                    detailsBuilder.append("Resolution: Unknown\n")
                }
                val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                if (fps != null && fps.isNotEmpty()) {
                    val fpsValue = fps.toFloatOrNull()?.toInt() ?: 30
                    detailsBuilder.append("Framerate: $fpsValue fps\n")
                } else {
                    val fpsFromName = extractFpsFromFileName(recording.name)
                    if (fpsFromName > 0) {
                        detailsBuilder.append("Framerate: $fpsFromName fps\n")
                    } else {
                        detailsBuilder.append("Framerate: 30 fps (estimated)\n")
                    }
                }
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                if (bitrate != null) {
                    val bitrateInt = bitrate.toIntOrNull() ?: 0
                    if (bitrateInt > 0) {
                        val formattedBitrate = when {
                            bitrateInt > 1_000_000 -> "${bitrateInt / 1_000_000} Mbps"
                            bitrateInt > 1000 -> "${bitrateInt / 1000} Kbps"
                            else -> "$bitrateInt bps"
                        }
                        detailsBuilder.append("Bitrate: $formattedBitrate\n")
                    }
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting video details: ${e.message}")
            }
        }
        detailsBuilder.append("Size: $fileSize\n")
        detailsBuilder.append("Duration: $duration\n")
        detailsBuilder.append("Created: $formattedDate\n")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Recording Details")
            .setMessage(detailsBuilder.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatDuration(durationMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        val kilobyte = 1024L
        val megabyte = kilobyte * 1024

        return when {
            sizeInBytes < kilobyte -> "$sizeInBytes B"
            sizeInBytes < megabyte -> "${sizeInBytes / kilobyte} KB"
            else -> String.format("%.2f MB", sizeInBytes.toDouble() / megabyte)
        }
    }

    private fun getOutputDirectory(): File {
        // Try multiple storage options for better compatibility with MIUI and other custom ROMs

        // Option 1: Use external media dirs (primary option)
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "Recordings").apply {
                if (!exists()) {
                    val success = mkdirs()
                    Log.d(TAG, "Creating directory ${absolutePath}, success: $success")
                }
            }
        }

        // Option 2: Use external files dir as fallback
        val externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let {
            File(it, "Recordings").apply {
                if (!exists()) {
                    val success = mkdirs()
                    Log.d(TAG, "Creating external files directory ${absolutePath}, success: $success")
                }
            }
        }

        // Option 3: Use internal storage as last resort
        val internalDir = File(filesDir, "Recordings").apply {
            if (!exists()) {
                val success = mkdirs()
                Log.d(TAG, "Creating internal directory ${absolutePath}, success: $success")
            }
        }

        // Use the first available directory
        val selectedDir = when {
            mediaDir != null && mediaDir.canWrite() -> mediaDir
            externalFilesDir != null && externalFilesDir.canWrite() -> externalFilesDir
            internalDir.canWrite() -> internalDir
            else -> filesDir // Last resort
        }

        Log.d(TAG, "Selected output directory: ${selectedDir.absolutePath}")
        return selectedDir
    }

    private fun setupVideoPlayerControls() {
        // Play/Pause button
        playPauseButton.setOnClickListener {
            if (videoPreview.togglePlayPause()) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        // Video preview listeners
        videoPreview.setOnPlayPauseListener { isPlaying ->
            playPauseButton.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }

        videoPreview.setOnCompletionListener {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
        }

        videoPreview.setOnPreparedListener { mp ->
            // Hide loading indicator
            showLoadingState(false)

            // Start video playback
            videoPreview.play()

            // Set up seek bar
            videoSeekBar.valueTo = videoPreview.getDuration().toFloat()
            videoSeekBar.value = 0f

            // Start seek bar update
            startSeekBarUpdate()

            // Update video details
            updateVideoDetails()
        }

        // Slider change listener
        videoSeekBar.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                videoPreview.seekTo(value.toInt())
                updateDurationText()
            }
        }

        // Updated toggle for video details
        findViewById<ImageButton>(R.id.toggleDetailsButton).setOnClickListener {
            if (videoDetailsPanel.visibility == View.VISIBLE) {
                videoDetailsPanel.visibility = View.GONE
            } else {
                updateVideoDetails()
                videoDetailsPanel.visibility = View.VISIBLE
            }
        }
    }

    private fun startSeekBarUpdate() {
        stopSeekBarUpdate()

        seekUpdateRunnable = object : Runnable {
            override fun run() {
                if (videoPreview.isAvailable) {
                    val currentPosition = videoPreview.getCurrentPosition()
                    videoSeekBar.value = currentPosition.toFloat()
                    updateDurationText()
                    seekUpdateHandler.postDelayed(this, 1000)
                }
            }
        }

        seekUpdateHandler.post(seekUpdateRunnable!!)
    }

    private fun stopSeekBarUpdate() {
        seekUpdateRunnable?.let {
            seekUpdateHandler.removeCallbacks(it)
            seekUpdateRunnable = null
        }
    }

    private fun updateDurationText() {
        val currentPosition = videoPreview.getCurrentPosition()
        val duration = videoPreview.getDuration()

        val currentFormatted = formatTime(currentPosition)
        val durationFormatted = formatTime(duration)

        durationText.text = "$currentFormatted / $durationFormatted"
    }

    private fun formatTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateVideoDetails() {
        currentRecording?.let { recording ->
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(recording.file.absolutePath)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (width != null && height != null) {
                    val qualityName = when {
                        height.toInt() >= 2160 -> "4K UHD (2160p)"
                        height.toInt() >= 1080 -> "Full HD (1080p)"
                        height.toInt() >= 720 -> "HD (720p)"
                        height.toInt() >= 480 -> "SD (480p)"
                        else -> "${height}p"
                    }
                    videoResolutionText.text = "Resolution: ${width}x${height} ($qualityName)"
                } else {
                    videoResolutionText.text = "Resolution: Unknown"
                }
                val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                if (fps != null && fps.isNotEmpty()) {
                    videoFpsText.text = "FPS: ${fps.toFloatOrNull()?.toInt() ?: 30}"
                } else {
                    val fpsFromName = extractFpsFromFileName(recording.name)
                    videoFpsText.text = if (fpsFromName > 0) "FPS: $fpsFromName" else "FPS: 30 (estimated)"
                }
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                if (bitrate != null) {
                    val bitrateInt = bitrate.toIntOrNull() ?: 0
                    videoBitrateText.text = if (bitrateInt > 0) {
                        val formattedBitrate = when {
                            bitrateInt > 1_000_000 -> "${bitrateInt / 1_000_000} Mbps"
                            bitrateInt > 1000 -> "${bitrateInt / 1000} Kbps"
                            else -> "$bitrateInt bps"
                        }
                        "Bitrate: $formattedBitrate"
                    } else "Bitrate: Unknown"
                } else {
                    videoBitrateText.text = "Bitrate: Unknown"
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating video details: ${e.message}")
            }
        }
    }

    // Helper to calculate estimated bitrate based on resolution and framerate
    private fun calculateEstimatedBitrate(width: Int, height: Int, fps: Int): String {
        // Simplified bitrate estimation using resolution and framerate
        val pixelCount = width * height
        val bitrate = when {
            pixelCount >= 3840 * 2160 -> 35 + (fps / 30) * 15  // 4K
            pixelCount >= 1920 * 1080 -> 8 + (fps / 30) * 7    // 1080p
            pixelCount >= 1280 * 720 -> 5 + (fps / 30) * 3     // 720p
            else -> 2 + (fps / 30)                           // 480p or lower
        }
        return bitrate.toString()
    }

    // Function to filter recordings based on search text and selected tab
    private fun filterRecordings() {
        val searchText = (findViewById<EditText>(R.id.searchInput).text.toString()).trim()
        val tabLayout = findViewById<TabLayout>(R.id.recordingTypeTabLayout)
        val selectedTab = tabLayout.selectedTabPosition // 0: All, 1: Video, 2: Audio Only
        val filtered = allRecordings.filter { recording ->
            val matchesTab = when (selectedTab) {
                1 -> recording.isVideo
                2 -> !recording.isVideo
                else -> true
            }
            val matchesSearch = if (searchText.isNotEmpty()) {
                recording.name.contains(searchText, ignoreCase = true)
            } else true
            matchesTab && matchesSearch
        }

        // Use DiffUtil for efficient updates
        val diffCallback = RecordingDiffCallback(recordings, filtered)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        recordings.clear()
        recordings.addAll(filtered)

        // Apply the calculated diff
        diffResult.dispatchUpdatesTo(adapter)
    }

    // DiffUtil callback for efficient RecyclerView updates
    private class RecordingDiffCallback(
        private val oldList: List<RecordingFile>,
        private val newList: List<RecordingFile>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].file.absolutePath == newList[newItemPosition].file.absolutePath
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.name == new.name && old.size == new.size && old.timestamp == new.timestamp
        }
    }
}

// Data class to hold recording file information
data class RecordingFile(
    val name: String,
    val size: Long,
    val timestamp: Long,
    val file: File,
    val isVideo: Boolean = true,
    val duration: Long = 0
) {
    val formattedDate: String
        get() {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }

    val formattedSize: String
        get() {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }

    val formattedDuration: String
        get() {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

// Adapter for the RecyclerView with DiffUtil for efficient updates
class RecordingsAdapter(
    private val recordings: List<RecordingFile>,
    private val onItemClick: (RecordingFile) -> Unit,
    private val onMenuClick: (View, RecordingFile) -> Unit,
    private val onThumbnailLoaded: (RecordingFile, Bitmap?) -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

    // Use LruCache for better memory management
    private val maxCacheSize = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val thumbnailCache = object : androidx.collection.LruCache<String, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            // Return the size of the bitmap in kilobytes
            return bitmap.byteCount / 1024
        }
    }

    // Use a more efficient thread pool with a queue
    private val thumbnailExecutor = Executors.newFixedThreadPool(2)
    private val metadataCache = mutableMapOf<String, VideoMetadata>()
    private val pendingThumbnailTasks = mutableMapOf<String, Boolean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = recordings[position]
        holder.bind(recording)

        // Load thumbnail if it's a video
        if (recording.isVideo) {
            val filePath = recording.file.absolutePath

            // Check cache first
            val cachedThumbnail = thumbnailCache.get(filePath)
            if (cachedThumbnail != null) {
                holder.thumbnailImageView.setImageBitmap(cachedThumbnail)
            } else {
                // Set placeholder first
                holder.thumbnailImageView.setImageResource(R.drawable.ic_videocam)

                // Only load if not already loading
                if (pendingThumbnailTasks[filePath] != true) {
                    pendingThumbnailTasks[filePath] = true

                    // Load thumbnail in background
                    thumbnailExecutor.execute {
                        try {
                            // Use a more efficient thumbnail generation
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 4  // Scale down to 1/4 size
                                inPreferredConfig = Bitmap.Config.RGB_565  // Use less memory
                            }

                            val bitmap = ThumbnailUtils.createVideoThumbnail(
                                filePath,
                                MediaStore.Images.Thumbnails.MINI_KIND
                            )

                            // Store in cache if not null
                            if (bitmap != null) {
                                thumbnailCache.put(filePath, bitmap)

                                // Extract video metadata for displaying quality (only if needed)
                                if (!metadataCache.containsKey(filePath)) {
                                    extractVideoMetadata(recording)
                                }

                                // Update UI on main thread
                                Handler(Looper.getMainLooper()).post {
                                    // Check if view is still visible and showing the same item
                                    if (holder.adapterPosition == position && holder.itemView.isAttachedToWindow) {
                                        holder.thumbnailImageView.setImageBitmap(bitmap)
                                        onThumbnailLoaded(recording, bitmap)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("RecordingsAdapter", "Error loading thumbnail: ${e.message}")
                        } finally {
                            pendingThumbnailTasks.remove(filePath)
                        }
                    }
                }
            }

            holder.qualityChip.visibility = View.GONE
        } else {
            // For audio, just show the audio icon
            holder.thumbnailImageView.setImageResource(R.drawable.ic_mic)
            holder.qualityChip.visibility = View.GONE
        }

        // Set click listeners
        holder.itemView.setOnClickListener {
            onItemClick(recording)
        }

        holder.menuButton.setOnClickListener {
            onMenuClick(it, recording)
        }
    }

    private fun extractVideoMetadata(recording: RecordingFile) {
        try {
            val filePath = recording.file.absolutePath
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)

            // Get resolution
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

            // Get framerate - try from metadata first
            var frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.toInt() ?: 0

            // If we couldn't get framerate from metadata, try from filename
            if (frameRate <= 0) {
                frameRate = RecordingsBrowserActivity.extractFpsFromFileName(recording.name)
            }

            // Default to 30 if we still don't have a value
            if (frameRate <= 0) {
                frameRate = 30
            }

            // Get bitrate
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            // If bitrate is 0, estimate it
            val finalBitrate = if (bitrate <= 0) {
                // Estimate bitrate based on resolution and framerate
                val pixelCount = width * height
                when {
                    pixelCount >= 3840 * 2160 -> (35 + (frameRate / 30) * 15) * 1_000_000  // 4K
                    pixelCount >= 1920 * 1080 -> (8 + (frameRate / 30) * 7) * 1_000_000    // 1080p
                    pixelCount >= 1280 * 720 -> (5 + (frameRate / 30) * 3) * 1_000_000     // 720p
                    else -> (2 + (frameRate / 30)) * 1_000_000                           // 480p or lower
                }
            } else {
                bitrate
            }

            // Store in cache
            metadataCache[filePath] = VideoMetadata(width, height, frameRate, finalBitrate)

            retriever.release()
        } catch (e: Exception) {
            Log.e("RecordingsAdapter", "Error extracting metadata: ${e.message}")
        }
    }

    override fun getItemCount() = recordings.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnailImageView: ImageView = view.findViewById(R.id.recordingThumbnail)
        val durationBadge: TextView = view.findViewById(R.id.recordingDuration)
        val recordingName: TextView = view.findViewById(R.id.recordingTitle)
        val recordingDate: TextView = view.findViewById(R.id.recordingDate)
        val recordingSize: TextView = view.findViewById(R.id.recordingSize)
        val qualityChip: Chip = view.findViewById(R.id.recordingQuality)
        val menuButton: ImageButton = view.findViewById(R.id.recordingMenu)
        val audioIndicator: ImageView = view.findViewById(R.id.audioIndicator)

        fun bind(recording: RecordingFile) {
            recordingName.text = recording.name
            recordingDate.text = recording.formattedDate
            recordingSize.text = recording.formattedSize
            durationBadge.text = recording.formattedDuration
        }

        fun updateQualityChip(metadata: VideoMetadata) {
            // Determine quality label based on height
            val resolution = when {
                metadata.height >= 2160 -> "4K"
                metadata.height >= 1080 -> "1080p"
                metadata.height >= 720 -> "720p"
                metadata.height >= 480 -> "480p"
                else -> "${metadata.height}p"
            }

            // Format the bitrate for display if needed
            val bitrateFormatted = when {
                metadata.bitrate > 1_000_000 -> "${metadata.bitrate / 1_000_000} Mbps"
                metadata.bitrate > 1000 -> "${metadata.bitrate / 1000} Kbps"
                else -> "${metadata.bitrate} bps"
            }

            // Show quality and framerate
            qualityChip.text = "$resolution • ${metadata.frameRate}fps"
            qualityChip.visibility = View.VISIBLE
        }
    }

    // Simple data class to hold video metadata
    data class VideoMetadata(
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val bitrate: Int
    )
}
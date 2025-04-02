package com.example.systemservice

import android.content.Context
import android.graphics.Matrix
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import androidx.core.view.GestureDetectorCompat
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper

/**
 * A TextureView that displays video with pinch-to-zoom and pan functionality
 */
class ZoomableTextureVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "ZoomableVideoView"
        const val MIN_SCALE = 1.0f
        const val MAX_SCALE = 5.0f
        
        // Increased scale step for smoother zoom
        private const val SCALE_STEP = 0.2f
        
        // Animation duration in ms
        private const val ANIMATION_DURATION = 200L
        
        // Double-tap zoom level
        private const val DOUBLE_TAP_ZOOM = 2.0f
    }

    // Matrix for transformations
    private val matrix = Matrix()
    
    // Scale and position tracking
    private var scale = 1.0f
    private var posX = 0f
    private var posY = 0f
    
    // Transition animation
    private var animationHandler = Handler(Looper.getMainLooper())
    private var isAnimating = false
    private var startScale = 1.0f
    private var targetScale = 1.0f
    private var startPosX = 0f
    private var startPosY = 0f
    private var targetPosX = 0f
    private var targetPosY = 0f
    private var animationStartTime = 0L
    
    // Focus point for zooming
    private var focusX = 0f
    private var focusY = 0f
    
    // Gesture detectors
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetectorCompat
    
    // Media player
    private var mediaPlayer: MediaPlayer? = null
    private var videoUri: Uri? = null
    private var isPrepared = false
    private var position = 0
    private var isPlaying = false
    private var isSurfaceCreated = false
    private var lastError: Exception? = null
    
    // Listeners
    private var onPlayPauseListener: ((Boolean) -> Unit)? = null
    private var onVideoCompletionListener: (() -> Unit)? = null
    private var onVideoPreparedListener: ((MediaPlayer) -> Unit)? = null
    private var onZoomChangeListener: ((Float) -> Unit)? = null
    private var onErrorListener: ((Exception) -> Unit)? = null

    init {
        surfaceTextureListener = this
        
        // Initialize gesture detectors
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetectorCompat(context, GestureListener())
        
        // Initial matrix setup
        updateMatrix()
    }
    
    // Set video URI
    fun setVideoUri(uri: Uri) {
        videoUri = uri
        if (isSurfaceCreated) {
            setupMediaPlayer()
        }
    }
    
    // Play the video
    fun play() {
        if (isPrepared) {
            try {
                mediaPlayer?.start()
                isPlaying = true
                onPlayPauseListener?.invoke(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback: ${e.message}")
                handleError(e)
            }
        } else {
            // If not prepared yet, mark as playing so it starts once prepared
            isPlaying = true
        }
    }
    
    // Pause the video
    fun pause() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            onPlayPauseListener?.invoke(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback: ${e.message}")
            handleError(e)
        }
    }
    
    // Toggle play/pause
    fun togglePlayPause(): Boolean {
        return if (isPlaying) {
            pause()
            false
        } else {
            play()
            true
        }
    }
    
    // Seek to position
    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking: ${e.message}")
            handleError(e)
        }
    }
    
    // Get current position
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting position: ${e.message}")
            0
        }
    }
    
    // Get video duration
    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting duration: ${e.message}")
            0
        }
    }
    
    // Set zoom level programmatically with animation
    fun setZoom(zoomLevel: Float, animate: Boolean = true) {
        val newScale = zoomLevel.coerceIn(MIN_SCALE, MAX_SCALE)
        if (animate) {
            animateZoom(newScale)
        } else {
            scale = newScale
            constrainPan()
            updateMatrix()
            onZoomChangeListener?.invoke(scale)
        }
    }
    
    // Animate zoom to target level
    private fun animateZoom(targetScale: Float) {
        if (isAnimating) {
            animationHandler.removeCallbacksAndMessages(null)
        }
        
        this.startScale = scale
        this.targetScale = targetScale
        startPosX = posX
        startPosY = posY
        
        // If zooming out, reset position to center
        if (targetScale <= 1.0f) {
            targetPosX = 0f
            targetPosY = 0f
        } else {
            targetPosX = posX
            targetPosY = posY
        }
        
        animationStartTime = System.currentTimeMillis()
        isAnimating = true
        
        animationHandler.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - animationStartTime
                val progress = (elapsed.toFloat() / ANIMATION_DURATION).coerceIn(0f, 1f)
                
                // Ease in-out interpolation
                val t = when {
                    progress < 0.5f -> 2 * progress * progress
                    else -> -1 + (4 - 2 * progress) * progress
                }
                
                scale = startScale + (targetScale - startScale) * t
                posX = startPosX + (targetPosX - startPosX) * t
                posY = startPosY + (targetPosY - startPosY) * t
                
                constrainPan()
                updateMatrix()
                onZoomChangeListener?.invoke(scale)
                
                if (progress < 1f) {
                    animationHandler.post(this)
                } else {
                    isAnimating = false
                }
            }
        })
    }
    
    // Get current zoom level
    fun getZoom(): Float = scale
    
    // Zoom in by a step amount
    fun zoomIn() {
        setZoom(scale + SCALE_STEP)
    }
    
    // Zoom out by a step amount
    fun zoomOut() {
        setZoom(scale - SCALE_STEP)
    }
    
    // Set on play/pause listener
    fun setOnPlayPauseListener(listener: (Boolean) -> Unit) {
        onPlayPauseListener = listener
    }
    
    // Set on completion listener
    fun setOnCompletionListener(listener: () -> Unit) {
        onVideoCompletionListener = listener
    }
    
    // Set on prepared listener
    fun setOnPreparedListener(listener: (MediaPlayer) -> Unit) {
        onVideoPreparedListener = listener
    }
    
    // Set on zoom change listener
    fun setOnZoomChangeListener(listener: (Float) -> Unit) {
        onZoomChangeListener = listener
    }
    
    // Set on error listener
    fun setOnErrorListener(listener: (Exception) -> Unit) {
        onErrorListener = listener
    }
    
    // Release resources
    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            isPrepared = false
            animationHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing: ${e.message}")
        }
    }
    
    // Reset zoom and position
    fun resetZoom() {
        setZoom(MIN_SCALE)
    }

    // Set up media player
    private fun setupMediaPlayer() {
        release()
        
        try {
            mediaPlayer = MediaPlayer()
            
            // Set up surface for rendering
            val surface = Surface(surfaceTexture)
            mediaPlayer?.setSurface(surface)
            
            // Set data source
            videoUri?.let { uri ->
                mediaPlayer?.setDataSource(context, uri)
                
                // Prepare asynchronously
                mediaPlayer?.prepareAsync()
                
                // Set up listeners
                mediaPlayer?.setOnPreparedListener { mp ->
                    isPrepared = true
                    mp.seekTo(position)
                    if (isPlaying) {
                        mp.start()
                    }
                    onVideoPreparedListener?.invoke(mp)
                }
                
                mediaPlayer?.setOnCompletionListener {
                    isPlaying = false
                    onPlayPauseListener?.invoke(false)
                    onVideoCompletionListener?.invoke()
                }
                
                mediaPlayer?.setOnErrorListener { _, what, extra ->
                    val errorMsg = "MediaPlayer error: what=$what, extra=$extra"
                    Log.e(TAG, errorMsg)
                    val error = Exception(errorMsg)
                    handleError(error)
                    true // Return true to indicate error was handled
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up media player: ${e.message}")
            handleError(e)
        }
    }
    
    private fun handleError(e: Exception) {
        lastError = e
        onErrorListener?.invoke(e)
    }
    
    // Update transformation matrix
    private fun updateMatrix() {
        // Don't update if width or height is zero
        if (width == 0 || height == 0) return
        
        matrix.reset()
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Use focus point for pinch zoom or center for other zooms
        val pivotX = if (isAnimating) centerX else focusX
        val pivotY = if (isAnimating) centerY else focusY
        
        // First translate to focus/pivot point
        matrix.postTranslate(-pivotX, -pivotY)
        // Then scale
        matrix.postScale(scale, scale)
        // Then translate back from focus/pivot point
        matrix.postTranslate(pivotX, pivotY)
        // Finally apply pan position
        matrix.postTranslate(posX, posY)
        
        setTransform(matrix)
        invalidate()
    }
    
    // Constrain pan position within bounds
    private fun constrainPan() {
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Calculate bounds based on scale
        val scaledWidth = width * scale
        val scaledHeight = height * scale
        
        val maxPanX = ((scaledWidth - width) / 2f).coerceAtLeast(0f)
        val maxPanY = ((scaledHeight - height) / 2f).coerceAtLeast(0f)
        
        // Only apply constraints if zoomed in
        if (scale > 1.0f) {
            posX = posX.coerceIn(-maxPanX, maxPanX)
            posY = posY.coerceIn(-maxPanY, maxPanY)
        } else {
            posX = 0f
            posY = 0f
        }
    }
    
    // Handle touch events
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Pass event to both detectors
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        
        // Cancel any ongoing animation when user touches
        if (event.action == MotionEvent.ACTION_DOWN && isAnimating) {
            isAnimating = false
            animationHandler.removeCallbacksAndMessages(null)
        }
        
        return true
    }

    // TextureView.SurfaceTextureListener methods
    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        isSurfaceCreated = true
        if (videoUri != null) {
            setupMediaPlayer()
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        updateMatrix()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        // Save current state
        isSurfaceCreated = false
        position = mediaPlayer?.currentPosition ?: 0
        isPlaying = mediaPlayer?.isPlaying ?: false
        
        // Release resources
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // Not needed
    }
    
    // Scale gesture listener for pinch zoom
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Save focus point for zooming
            focusX = detector.focusX
            focusY = detector.focusY
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Update scale with a smooth factor
            scale *= detector.scaleFactor
            scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
            
            // Update position constraints
            constrainPan()
            
            // Apply transformation
            updateMatrix()
            
            // Notify listener
            onZoomChangeListener?.invoke(scale)
            
            return true
        }
    }
    
    // Gesture listener for panning and double tap
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            // Only allow panning if zoomed in
            if (scale > 1.0f) {
                posX -= distanceX
                posY -= distanceY
                
                // Constrain pan
                constrainPan()
                
                // Apply transformation
                updateMatrix()
                return true
            }
            return false
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Set focus point to double tap location
            focusX = e.x
            focusY = e.y
            
            // Toggle between min zoom and double tap zoom level
            val newScale = if (scale > MIN_SCALE) MIN_SCALE else DOUBLE_TAP_ZOOM
            
            // Animate to new scale
            setZoom(newScale, true)
            
            return true
        }
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Only toggle play/pause if media is prepared, to avoid interfering with zoom gestures
            if (isPrepared) {
                togglePlayPause()
            }
            return true
        }
    }
} 
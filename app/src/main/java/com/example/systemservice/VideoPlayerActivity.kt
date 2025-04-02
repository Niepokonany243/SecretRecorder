package com.example.systemservice

import com.example.systemservice.R

import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import android.view.View
import android.os.Build
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController

class VideoPlayerActivity : SecretRecorderBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set additional window flags before setContentView
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        
        setContentView(R.layout.activity_video_player)
        
        // Hide the action bar completely
        supportActionBar?.hide()
        
        // Set proper fullscreen flags to completely hide system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // For versions below Android 11
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }

        // Setup VideoView to play a video if provided
        val videoView = findViewById<VideoView>(R.id.videoView)
        val videoUri: Uri? = intent.getParcelableExtra("videoUri")
        videoUri?.let {
            videoView.setVideoURI(it)
            videoView.start()
        }

        // Add a click/touch listener on the video view for exiting or showing controls
        videoView.setOnClickListener {
            onBackPressed()
        }
    }
} 
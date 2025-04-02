package com.example.systemservice

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sqrt

class BatteryOptimizationUtil(
    private val context: Context,
    private val motionCallback: (Boolean) -> Unit
) : SensorEventListener {
    
    companion object {
        private const val TAG = "BatteryOptimizationUtil"
        private const val MOTION_THRESHOLD = 0.5f // Threshold for detecting significant motion
        private const val STATIONARY_DELAY_MS = 60000 // Delay before considering device stationary (1 minute)
    }
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isFirstReading = true
    private var isDeviceInMotion = false
    private val handler = Handler(Looper.getMainLooper())
    private var stationaryRunnable: Runnable? = null
    
    /**
     * Start monitoring device motion.
     */
    fun startMonitoring() {
        if (accelerometer != null) {
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Motion monitoring started")
        } else {
            Log.e(TAG, "Accelerometer sensor not available")
            // Always consider device in motion if sensor not available
            motionCallback(true)
        }
    }
    
    /**
     * Stop monitoring device motion.
     */
    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
        stationaryRunnable?.let { handler.removeCallbacks(it) }
        Log.d(TAG, "Motion monitoring stopped")
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            if (isFirstReading) {
                lastX = x
                lastY = y
                lastZ = z
                isFirstReading = false
                return
            }
            
            // Calculate the change in accelerometer values
            val deltaX = Math.abs(lastX - x)
            val deltaY = Math.abs(lastY - y)
            val deltaZ = Math.abs(lastZ - z)
            
            // Calculate the overall magnitude of change
            val movement = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
            
            // Update last known values
            lastX = x
            lastY = y
            lastZ = z
            
            // Check if movement is above threshold
            if (movement > MOTION_THRESHOLD) {
                handleDeviceMotion()
            } else {
                // If device is still, schedule a check to see if it remains still
                scheduleStationaryCheck()
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    private fun handleDeviceMotion() {
        // Remove any pending stationary checks
        stationaryRunnable?.let { handler.removeCallbacks(it) }
        
        // Only notify if state changed
        if (!isDeviceInMotion) {
            isDeviceInMotion = true
            Log.d(TAG, "Device in motion detected")
            motionCallback(true)
        }
    }
    
    private fun scheduleStationaryCheck() {
        // Remove any existing callbacks
        stationaryRunnable?.let { handler.removeCallbacks(it) }
        
        // Create new runnable for stationary check
        stationaryRunnable = Runnable {
            if (isDeviceInMotion) {
                isDeviceInMotion = false
                Log.d(TAG, "Device stationary detected")
                motionCallback(false)
            }
        }
        
        // Schedule the check after the delay
        handler.postDelayed(stationaryRunnable!!, STATIONARY_DELAY_MS.toLong())
    }
    
    /**
     * Bypass battery optimizations for specific devices.
     * This method documents how to bypass battery optimization on different manufacturers.
     */
    fun documentBatteryOptimizationBypass() {
        Log.d(TAG, "Battery Optimization Bypass Documentation:")
        
        // Xiaomi Battery Saver Bypass
        Log.d(TAG, "Xiaomi Battery Saver Bypass: " +
                "1. Settings > Apps > Manage Apps > [Your App] > Battery > No restrictions " +
                "2. Settings > Battery & Performance > App Battery Saver > [Your App] > No restrictions " +
                "3. Use foreground service with partial wake lock")
        
        // Samsung App Optimization Bypass
        Log.d(TAG, "Samsung App Optimization Bypass: " +
                "1. Settings > Apps > [Your App] > Battery > Unrestricted " +
                "2. Settings > Device Care > Battery > Background Usage Limits > Add [Your App] to exclusions " +
                "3. Use PowerManager.isIgnoringBatteryOptimizations() check")
        
        // Android 14 Foreground Service Restrictions Bypass
        Log.d(TAG, "Android 14 Foreground Service Restrictions Bypass: " +
                "1. Use FOREGROUND_SERVICE_MEDIA_PROJECTION permission " +
                "2. Set android:foregroundServiceType=\"mediaProjection\" in manifest " +
                "3. Use startForeground() with low-importance notification " +
                "4. Use WorkManager for scheduling periodic background tasks")
    }
} 
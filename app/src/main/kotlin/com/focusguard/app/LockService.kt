package com.focusguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.focusguard.app.data.PreferenceManager
import com.focusguard.app.data.dataStore

class LockService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private var overlayView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var targetTimeMillis: Long = 0L
    private var isScreenOn = true
    private var isCallActive = false
    
    private lateinit var preferenceManager: PreferenceManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var updateTimerRunnable: Runnable? = null
    
    // Lifecycle components for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    // State for UI
    private var remainingTimeText by mutableStateOf("00:00:00")
    
    // Screen receiver to handle screen on/off
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned OFF - Pausing timer updates")
                    isScreenOn = false
                    stopTimerUpdates()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen turned ON - Resuming timer updates")
                    isScreenOn = true
                    updateRemainingTime()
                    startTimerUpdates()
                }
            }
        }
    }
    
    // Phone state listener for call handling
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        preferenceManager = PreferenceManager(this)
        
        // Register screen receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        
        // Register phone state listener
        registerPhoneStateListener()
        
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 0) ?: 0
        
        if (durationMinutes > 0) {
            targetTimeMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            
            // Start foreground service
            startForeground(NOTIFICATION_ID, createNotification())
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            
            // Create and show overlay
            createOverlay()
            
            // Start timer updates
            updateRemainingTime()
            startTimerUpdates()
            
            Log.d(TAG, "Lock started for $durationMinutes minutes")
        }
        
        // Return START_NOT_STICKY for safety - service won't restart after device reboot
        return START_NOT_STICKY
    }

    private fun createOverlay() {
        try {
            overlayView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@LockService)
                setViewTreeSavedStateRegistryOwner(this@LockService)
                
                setContent {
                    MaterialTheme {
                        LockOverlayContent()
                    }
                }
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or  // Allow system keys (Power, Volume)
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            
            windowManager?.addView(overlayView, params)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            
            Log.d(TAG, "Overlay created and displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating overlay", e)
        }
    }

    @Composable
    private fun LockOverlayContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = remainingTimeText,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D9FF)
            )
        }
    }

    private fun startTimerUpdates() {
        updateTimerRunnable = object : Runnable {
            override fun run() {
                if (isScreenOn && !isCallActive) {
                    updateRemainingTime()
                    
                    val remainingMillis = targetTimeMillis - System.currentTimeMillis()
                    if (remainingMillis <= 0) {
                        // Lock duration completed
                        handleCompletion()
                    } else {
                        handler.postDelayed(this, 1000) // Update every second
                    }
                }
            }
        }
        handler.post(updateTimerRunnable!!)
    }
    
    private fun handleCompletion() {
        // Run coroutine to check preference
        lifecycleScope.launch {
            val playSound = preferenceManager.isSoundEnabled.first()
            performCompletionFeedback(playSound)
            stopSelf()
        }
    }
    
    private fun performCompletionFeedback(playSound: Boolean) {
        // Vibrate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
        
        // Play Sound if enabled
        if (playSound) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(applicationContext, notification)
                r.play()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound", e)
            }
        }
    }

    private fun stopTimerUpdates() {
        updateTimerRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun updateRemainingTime() {
        val remainingMillis = targetTimeMillis - System.currentTimeMillis()
        
        if (remainingMillis <= 0) {
            remainingTimeText = "00:00:00"
        } else {
            val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
            
            remainingTimeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    private fun registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ - Use TelephonyCallback
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChange(state)
                }
            }
            telephonyManager?.registerTelephonyCallback(
                mainExecutor,
                telephonyCallback as TelephonyCallback
            )
        } else {
            // API < 31 - Use PhoneStateListener
            @Suppress("DEPRECATION")
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChange(state)
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING, TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Incoming call or call active - Hide overlay
                isCallActive = true
                hideOverlay()
                Log.d(TAG, "Call active - Overlay hidden")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended - Show overlay after 2 seconds delay
                isCallActive = false
                handler.postDelayed({
                    showOverlay()
                    Log.d(TAG, "Call ended - Overlay shown after 2s delay")
                }, 2000)
            }
        }
    }

    private fun hideOverlay() {
        try {
            overlayView?.visibility = android.view.View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay", e)
        }
    }

    private fun showOverlay() {
        try {
            overlayView?.visibility = android.view.View.VISIBLE
            updateRemainingTime()
            if (isScreenOn) {
                startTimerUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "focus_lock_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Lock",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the focus lock service running"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return Notification.Builder(this, channelId)
            .setContentTitle("FocusGuard Active")
            .setContentText("Focus mode is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Stop timer
        stopTimerUpdates()
        
        // Unregister screen receiver
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering screen receiver", e)
        }
        
        // Unregister phone state listener
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let {
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering phone state listener", e)
        }
        
        // Remove overlay
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
        
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val TAG = "LockService"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
    }
}

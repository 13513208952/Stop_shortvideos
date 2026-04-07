package com.android.webview

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var iconHideManager: IconHideManager

    private lateinit var tvServiceStatus: TextView
    private lateinit var tvDetectionStatus: TextView
    private lateinit var tvRecentsStatus: TextView
    private lateinit var btnOpenAccessibility: MaterialButton
    private lateinit var etHours: TextInputEditText
    private lateinit var etMinutes: TextInputEditText
    private lateinit var etSeconds: TextInputEditText
    private lateinit var btnTimerHide: MaterialButton
    private lateinit var btnPermanentHide: MaterialButton
    private lateinit var btnShowIcon: MaterialButton
    private lateinit var btnToggleRecents: MaterialButton

    private val statusRefreshHandler = Handler(Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            statusRefreshHandler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPreferences(this)
        iconHideManager = IconHideManager(this)

        // If exclude-from-recents is enabled and we weren't already launched with the flag,
        // relaunch ourselves with FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        val alreadyExcluded = (intent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0
        if (prefs.excludeFromRecents && !alreadyExcluded) {
            finishAndRemoveTask()
            val relaunch = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            startActivity(relaunch)
            return
        }

        setContentView(R.layout.activity_main)
        initViews()
        setupListeners()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        statusRefreshHandler.postDelayed(statusRefreshRunnable, 2000)
    }

    override fun onPause() {
        super.onPause()
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
    }

    override fun onStop() {
        super.onStop()
        // If "hide from recents" is enabled, remove from recents when going to background
        if (prefs.excludeFromRecents) {
            finishAndRemoveTask()
        }
    }

    private fun initViews() {
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvDetectionStatus = findViewById(R.id.tvDetectionStatus)
        tvRecentsStatus = findViewById(R.id.tvRecentsStatus)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        etHours = findViewById(R.id.etHours)
        etMinutes = findViewById(R.id.etMinutes)
        etSeconds = findViewById(R.id.etSeconds)
        btnTimerHide = findViewById(R.id.btnTimerHide)
        btnPermanentHide = findViewById(R.id.btnPermanentHide)
        btnShowIcon = findViewById(R.id.btnShowIcon)
        btnToggleRecents = findViewById(R.id.btnToggleRecents)
    }

    private fun setupListeners() {
        // Open accessibility settings
        btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Timer hide icon
        btnTimerHide.setOnClickListener {
            val hoursStr = etHours.text?.toString() ?: "0"
            val minutesStr = etMinutes.text?.toString() ?: "0"
            val secondsStr = etSeconds.text?.toString() ?: "0"

            val hours = hoursStr.toLongOrNull() ?: 0L
            val minutes = minutesStr.toLongOrNull() ?: 0L
            val seconds = secondsStr.toLongOrNull() ?: 0L

            if (hours == 0L && minutes == 0L && seconds == 0L) {
                Toast.makeText(this, R.string.toast_timer_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            iconHideManager.hideIconForDuration(hours, minutes, seconds)

            val timeDesc = buildString {
                if (hours > 0) append("${hours}小时")
                if (minutes > 0) append("${minutes}分钟")
                if (seconds > 0) append("${seconds}秒")
            }
            Toast.makeText(
                this,
                getString(R.string.toast_icon_hidden_timer, timeDesc),
                Toast.LENGTH_LONG
            ).show()
        }

        // Permanent hide icon
        btnPermanentHide.setOnClickListener {
            iconHideManager.hideIconPermanently()
            Toast.makeText(this, R.string.toast_icon_hidden_permanent, Toast.LENGTH_LONG).show()
        }

        // Show icon
        btnShowIcon.setOnClickListener {
            iconHideManager.showIcon()
            Toast.makeText(this, R.string.toast_icon_shown, Toast.LENGTH_SHORT).show()
        }

        // Toggle recents hiding
        btnToggleRecents.setOnClickListener {
            val newState = !prefs.excludeFromRecents
            prefs.excludeFromRecents = newState
            if (newState) {
                Toast.makeText(this, R.string.toast_recents_hidden, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_recents_shown, Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        // Service status
        val serviceRunning = BlockAccessibilityService.isRunning
        tvServiceStatus.text = if (serviceRunning) {
            getString(R.string.service_status_on)
        } else {
            getString(R.string.service_status_off)
        }

        // Detection status
        val detectionState = BlockAccessibilityService.currentDetectionState
        tvDetectionStatus.text = when (detectionState) {
            BlockAccessibilityService.DetectionState.IDLE ->
                getString(R.string.detection_status_idle)
            BlockAccessibilityService.DetectionState.NORMAL_DETECTING ->
                getString(R.string.detection_status_normal)
            BlockAccessibilityService.DetectionState.ACTIVATED ->
                getString(R.string.detection_status_activated)
        }

        // Recents status
        val excludeFromRecents = prefs.excludeFromRecents
        tvRecentsStatus.text = if (excludeFromRecents) {
            getString(R.string.recents_status_hidden)
        } else {
            getString(R.string.recents_status_visible)
        }
        btnToggleRecents.text = if (excludeFromRecents) {
            getString(R.string.btn_disable_hide_recents)
        } else {
            getString(R.string.btn_enable_hide_recents)
        }
    }
}



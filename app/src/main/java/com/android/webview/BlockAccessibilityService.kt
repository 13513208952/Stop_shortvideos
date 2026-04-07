package com.android.webview

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BlockAccessibility"
        private const val NORMAL_INTERVAL_MS = 10_000L   // 10 seconds
        private const val ACTIVATED_INTERVAL_MS = 2_000L  // 2 seconds
        private const val ACTIVATED_DURATION_MS = 30 * 60 * 1000L  // 30 minutes

        @Volatile
        private var instance: BlockAccessibilityService? = null

        val isRunning: Boolean
            get() = instance != null

        val currentDetectionState: DetectionState
            get() = instance?.detectionState ?: DetectionState.IDLE

        fun onScreenUnlocked() {
            instance?.handleScreenUnlocked()
        }
    }

    enum class DetectionState {
        IDLE,
        NORMAL_DETECTING,    // 10s interval
        ACTIVATED            // 2s interval
    }

    private lateinit var modelHelper: ModelInferenceHelper
    private lateinit var iconHideManager: IconHideManager
    private val handler = Handler(Looper.getMainLooper())
    private val inferenceExecutor: Executor = Executors.newSingleThreadExecutor()

    private var detectionState = DetectionState.IDLE
    private var isDetectionLoopRunning = false
    private var consecutiveNotRelatedCount = 0
    private var activatedUntilTime = 0L  // SystemClock timestamp when activated mode expires

    // Screen unlock receiver (registered dynamically for reliability)
    private var screenUnlockReceiver: ScreenUnlockReceiver? = null

    private val detectionRunnable = object : Runnable {
        override fun run() {
            if (!isDetectionLoopRunning) return
            performDetection()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        instance = this

        modelHelper = ModelInferenceHelper(this)
        modelHelper.loadModel()

        iconHideManager = IconHideManager(this)

        // Register screen unlock receiver dynamically
        screenUnlockReceiver = ScreenUnlockReceiver()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(screenUnlockReceiver, filter)

        // Start detection immediately (user may already be on screen)
        startDetectionLoop(DetectionState.NORMAL_DETECTING)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to process individual accessibility events
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        Log.i(TAG, "Accessibility service destroyed")
        stopDetectionLoop()
        modelHelper.close()
        screenUnlockReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        instance = null
        super.onDestroy()
    }

    private fun handleScreenUnlocked() {
        Log.i(TAG, "Screen unlocked — starting normal detection")
        startDetectionLoop(DetectionState.NORMAL_DETECTING)
    }

    private fun startDetectionLoop(initialState: DetectionState) {
        detectionState = initialState
        consecutiveNotRelatedCount = 0
        if (!isDetectionLoopRunning) {
            isDetectionLoopRunning = true
            handler.post(detectionRunnable)
        }
    }

    private fun stopDetectionLoop() {
        isDetectionLoopRunning = false
        detectionState = DetectionState.IDLE
        handler.removeCallbacks(detectionRunnable)
    }

    private fun performDetection() {
        if (!isDetectionLoopRunning) return

        // Check if timed icon-hide has expired
        iconHideManager.checkAndRestoreIcon()

        // Take screenshot using accessibility API (API 30+)
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                inferenceExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )

                        if (bitmap != null) {
                            // IMPORTANT: Must copy BEFORE closing the hardware buffer,
                            // otherwise the GPU memory is freed and copy() will crash
                            // with SIGABRT (pthread_mutex_lock on destroyed mutex).
                            val softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            bitmap.recycle()
                            screenshot.hardwareBuffer.close()

                            if (softBitmap != null) {
                                processScreenshot(softBitmap)
                                softBitmap.recycle()
                            } else {
                                scheduleNext()
                            }
                        } else {
                            screenshot.hardwareBuffer.close()
                            Log.w(TAG, "Failed to create bitmap from screenshot")
                            scheduleNext()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Screenshot failed with error code: $errorCode")
                        scheduleNext()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error taking screenshot", e)
            scheduleNext()
        }
    }

    private fun processScreenshot(bitmap: Bitmap) {
        val result = modelHelper.classify(bitmap)
        Log.i(TAG, "Classification result: $result (state=$detectionState)")

        when (result) {
            ModelInferenceHelper.ClassificationResult.SHORT_VIDEO -> {
                // Perform back action
                Log.i(TAG, "Short video detected! Performing BACK action")
                performGlobalAction(GLOBAL_ACTION_BACK)
                // Enter / reset activated mode for 30 minutes
                detectionState = DetectionState.ACTIVATED
                activatedUntilTime = android.os.SystemClock.elapsedRealtime() + ACTIVATED_DURATION_MS
                consecutiveNotRelatedCount = 0
            }
            ModelInferenceHelper.ClassificationResult.NOT_RELATED -> {
                // In activated mode, check if 30-min window has expired
                if (detectionState == DetectionState.ACTIVATED) {
                    if (android.os.SystemClock.elapsedRealtime() >= activatedUntilTime) {
                        Log.i(TAG, "Activated mode expired (30 min), returning to normal detection")
                        detectionState = DetectionState.NORMAL_DETECTING
                        consecutiveNotRelatedCount = 0
                    }
                }
            }
        }

        scheduleNext()
    }

    private fun scheduleNext() {
        if (!isDetectionLoopRunning) return

        val interval = when (detectionState) {
            DetectionState.IDLE -> return
            DetectionState.NORMAL_DETECTING -> NORMAL_INTERVAL_MS
            DetectionState.ACTIVATED -> ACTIVATED_INTERVAL_MS
        }
        handler.postDelayed(detectionRunnable, interval)
    }
}

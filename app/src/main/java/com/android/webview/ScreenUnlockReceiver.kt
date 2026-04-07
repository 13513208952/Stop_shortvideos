package com.android.webview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives ACTION_USER_PRESENT when the user unlocks the screen.
 * Notifies the BlockAccessibilityService to begin detection.
 */
class ScreenUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenUnlockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            Log.i(TAG, "Screen unlocked — notifying service to start detection")
            BlockAccessibilityService.onScreenUnlocked()
        }
    }
}


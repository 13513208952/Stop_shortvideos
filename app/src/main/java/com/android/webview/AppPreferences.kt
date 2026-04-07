package com.android.webview

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "block_short_videos_prefs"
        private const val KEY_ICON_HIDDEN = "icon_hidden"
        private const val KEY_ICON_UNHIDE_TIMESTAMP = "icon_unhide_timestamp"
        private const val KEY_ICON_PERMANENT_HIDE = "icon_permanent_hide"
        private const val KEY_EXCLUDE_FROM_RECENTS = "exclude_from_recents"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isIconHidden: Boolean
        get() = prefs.getBoolean(KEY_ICON_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_ICON_HIDDEN, value).apply()

    var iconUnhideTimestamp: Long
        get() = prefs.getLong(KEY_ICON_UNHIDE_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_ICON_UNHIDE_TIMESTAMP, value).apply()

    var isIconPermanentlyHidden: Boolean
        get() = prefs.getBoolean(KEY_ICON_PERMANENT_HIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_ICON_PERMANENT_HIDE, value).apply()

    var excludeFromRecents: Boolean
        get() = prefs.getBoolean(KEY_EXCLUDE_FROM_RECENTS, false)
        set(value) = prefs.edit().putBoolean(KEY_EXCLUDE_FROM_RECENTS, value).apply()
}


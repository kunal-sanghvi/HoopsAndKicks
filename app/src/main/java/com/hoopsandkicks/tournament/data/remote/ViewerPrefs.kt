package com.hoopsandkicks.tournament.data.remote

import android.content.Context

/**
 * Remembers the one room this device was last watching, so a viewer who presses back or fully exits
 * the app is never forced to re-type the code: reopening the app (or just returning to Home) can
 * resume the same room in one tap. Survives process death (SharedPreferences), unlike [ViewerStore]'s
 * in-memory state.
 *
 * Cleared automatically once the room is confirmed gone (see [ViewerStore.join]'s handling of
 * [JoinState.NotFound]), and by [clear] when the viewer deliberately stops watching.
 */
class ViewerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(code: String) {
        prefs.edit().putString(KEY_CODE, code).apply()
    }

    fun load(): String? = prefs.getString(KEY_CODE, null)

    fun clear() {
        prefs.edit().remove(KEY_CODE).apply()
    }

    private companion object {
        const val PREFS_NAME = "viewer"
        const val KEY_CODE = "last_room_code"
    }
}

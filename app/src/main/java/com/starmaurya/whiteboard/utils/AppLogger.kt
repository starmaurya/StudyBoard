package com.starmaurya.whiteboard.utils

import android.util.Log
import com.starmaurya.whiteboard.BuildConfig

object AppLogger {
    /**
     * Logs error messages and exceptions.
     * - In DEBUG build: logs to Logcat
     * - In RELEASE build: logs to Firebase Crashlytics
     */
    fun e(tag: String, exception: Exception) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, exception.message ?: "Unknown error", exception)
        } else {
            // Log non-fatal exception to Crashlytics
            // FirebaseCrashlytics.getInstance().recordException(exception)
        }
    }
}
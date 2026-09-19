package com.autumn.douyin.liquidglass

import android.util.Log

/**
 * Centralized logging. All logs share one tag so you can filter with
 * `adb logcat -s DouyinLiquidGlass`.
 */
object ModuleLog {

    private const val TAG = "DouyinLiquidGlass"

    @Volatile
    var verbose: Boolean = true

    fun d(message: String) {
        if (verbose) Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }
}

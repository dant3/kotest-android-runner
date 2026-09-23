package io.github.dant3.kotest.android.internal

import android.util.Log

/** Logcat tag for everything the runner has to say beyond test results, which JUnit has no channel for. */
internal const val LOG_TAG: String = "KotestAndroidRunner"

/** Logs a warning; a no-op off-device, where `android.util.Log` is only a stub. */
internal fun logWarning(message: String) {
    runCatching { Log.w(LOG_TAG, message) }
}

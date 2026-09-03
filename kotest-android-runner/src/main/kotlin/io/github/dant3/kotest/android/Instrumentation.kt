package io.github.dant3.kotest.android

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry

/** The [Instrumentation] the tests are running under. */
public val instrumentation: Instrumentation
    get() = InstrumentationRegistry.getInstrumentation()

/** Context of the application under test — the one you usually want in assertions. */
public val targetContext: Context
    get() = instrumentation.targetContext

/** Context of the test APK itself, e.g. to read test-only resources and assets. */
public val testContext: Context
    get() = instrumentation.context

/** Arguments passed to the instrumentation (`-e key value` / `testInstrumentationRunnerArguments`). */
public val instrumentationArguments: Bundle
    get() = InstrumentationRegistry.getArguments()

/**
 * Runs [block] on the application main thread and waits for it, returning its result.
 *
 * Kotest tests execute on the instrumentation thread, so anything touching views or other
 * main-thread-confined state has to be dispatched explicitly.
 */
public fun <T> onMainThread(block: () -> T): T {
    var result: Result<T>? = null
    instrumentation.runOnMainSync { result = runCatching(block) }
    return checkNotNull(result) { "runOnMainSync returned without executing the block" }.getOrThrow()
}

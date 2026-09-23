package io.github.dant3.kotest.android.internal

import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import org.junit.runner.manipulation.Filter

/** Marker reason used to hide tests that were not selected from the JUnit report entirely. */
internal const val FILTERED_BY_INSTRUMENTATION: String = "kotest-android: filtered out by the instrumentation runner"

/**
 * Decides which Kotest tests execute, applied as a [TestCaseExtension] so that a test that is not
 * selected is skipped before its body, and its `beforeTest`/`afterTest` callbacks, ever run.
 *
 * A Kotest `DescriptorFilter` would not do: nested tests are only discovered by executing their
 * container, which a filter over the not-yet-known children would reject.
 */
internal class TestSelection private constructor(
    private val includes: (TestCase) -> Boolean,
) : TestCaseExtension {
    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult = if (includes(testCase)) execute(testCase) else TestResult.Ignored(FILTERED_BY_INSTRUMENTATION)

    companion object {
        /**
         * Runs the selected tests together with everything beneath them, and the containers on the
         * way down to them — but not their siblings, whose bodies may well be tests themselves
         * (a `withData` row is a container).
         */
        fun of(request: TestRequest): TestSelection = TestSelection { testCase ->
            val path = testCase.testPath
            request.selected.any { selected -> path.isAtOrUnder(selected) || selected.isAtOrUnder(path) }
        }

        /**
         * Applies the JUnit [filter] (sharding, annotation filters, …) to the root tests — the ones
         * announced in the runner's description — and runs each selected root in full.
         */
        fun of(filter: Filter, specClass: Class<*>): TestSelection = TestSelection { testCase ->
            testCase.parent != null || filter.shouldRun(describeTest(specClass, testCase.testPath))
        }
    }
}

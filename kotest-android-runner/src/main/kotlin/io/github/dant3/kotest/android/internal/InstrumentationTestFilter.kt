package io.github.dant3.kotest.android.internal

import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.engine.test.TestResult
import org.junit.runner.manipulation.Filter

/** Marker reason used to hide filtered-out tests from the JUnit report entirely. */
internal const val FILTERED_BY_INSTRUMENTATION: String = "kotest-android: filtered out by the instrumentation runner"

/**
 * Applies the JUnit 4 [Filter] that `AndroidJUnitRunner` builds from `-e class Foo#test name`
 * (which is what `connectedAndroidTest --tests`, Android Studio's "run single test" and Test
 * Lab sharding all end up using).
 *
 * The filter is applied as a [TestCaseExtension] rather than a Kotest `DescriptorFilter`
 * because Kotest discovers nested tests lazily: at filter time a container has no children yet,
 * so JUnit's method filters would reject the whole branch and skip the very test that was
 * selected. Containers are therefore always executed, and only leaves are filtered.
 */
internal class InstrumentationTestFilter(
    private val filter: Filter,
) : TestCaseExtension {
    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult = when {
        testCase.type == TestType.Container -> execute(testCase)
        filter.shouldRun(describeTest(testCase)) -> execute(testCase)
        else -> TestResult.Ignored(FILTERED_BY_INSTRUMENTATION)
    }
}

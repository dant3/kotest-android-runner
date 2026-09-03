package io.github.dant3.kotest.android.internal

import io.kotest.common.KotestInternal
import io.kotest.core.spec.SpecRef
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.engine.listener.AbstractTestEngineListener
import io.kotest.engine.test.TestResult
import kotlin.reflect.KClass
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier

/**
 * Translates Kotest engine callbacks into the JUnit 4 [RunNotifier] events that
 * `AndroidJUnitRunner` (and everything reading its output — Gradle, Android Studio, Firebase
 * Test Lab) understands.
 *
 * Kotest containers have no JUnit 4 equivalent, so they are not reported as tests. A container
 * that fails *before* producing any leaf would otherwise vanish from the report, so container
 * failures are reported as a synthetic test failure under the container's own description.
 */
@OptIn(KotestInternal::class)
internal class AndroidTestEngineListener(
    private val notifier: RunNotifier,
    private val specClass: Class<*>,
) : AbstractTestEngineListener() {
    override suspend fun testStarted(testCase: TestCase) {
        if (testCase.type == TestType.Container) return
        notifier.fireTestStarted(describeTest(testCase))
    }

    override suspend fun testFinished(testCase: TestCase, result: TestResult) {
        val description = describeTest(testCase)
        when {
            result is TestResult.Ignored -> fireIgnored(description, result.reason)
            result.isErrorOrFailure -> fireFailure(description, result.errorOrNull, testCase.type)
            testCase.type == TestType.Container -> Unit
            else -> notifier.fireTestFinished(description)
        }
    }

    override suspend fun testIgnored(testCase: TestCase, reason: String?) {
        fireIgnored(describeTest(testCase), reason)
    }

    override suspend fun specFinished(ref: SpecRef, result: TestResult) {
        // A spec that blows up during instantiation or in beforeSpec never reports a test,
        // so surface the failure against the spec itself.
        val error = result.errorOrNull ?: return
        val description = Description.createTestDescription(specClass.name, SPEC_FAILURE_NAME)
        notifier.fireTestStarted(description)
        notifier.fireTestFailure(Failure(description, error))
        notifier.fireTestFinished(description)
    }

    override suspend fun specIgnored(kclass: KClass<*>, reason: String?) {
        notifier.fireTestIgnored(describeSpec(specClass))
    }

    private fun fireIgnored(description: Description, reason: String?) {
        if (reason == FILTERED_BY_INSTRUMENTATION) return
        notifier.fireTestIgnored(description)
    }

    private fun fireFailure(description: Description, error: Throwable?, type: TestType) {
        if (type == TestType.Container) notifier.fireTestStarted(description)
        notifier.fireTestFailure(Failure(description, error ?: IllegalStateException("Test failed without an error")))
        notifier.fireTestFinished(description)
    }

    private companion object {
        const val SPEC_FAILURE_NAME = "spec initialization"
    }
}

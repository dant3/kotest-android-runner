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
 * Test Lab, Android Test Orchestrator) understands.
 *
 * JUnit 4 has no notion of a container, so what counts as a JUnit test is a Kotest test without
 * children: every leaf, but also every container that turns out not to register anything — which is
 * what a `withData` row is, its body being the test itself. Whether a container has children is only
 * known once it finishes, so this listener keeps track of which tests turned out to be parents.
 *
 * Kotest delivers these callbacks one at a time (the launcher wraps listeners in a
 * `ThreadSafeTestEngineListener`), so the state here needs no synchronisation.
 */
@OptIn(KotestInternal::class)
internal abstract class AndroidTestEngineListener(
    protected val notifier: RunNotifier,
    protected val specClass: Class<*>,
) : AbstractTestEngineListener() {
    private val parents = mutableSetOf<String>()

    final override suspend fun testStarted(testCase: TestCase) {
        markAsChild(testCase)
        onStarted(testCase)
    }

    final override suspend fun testIgnored(testCase: TestCase, reason: String?) {
        if (reason == FILTERED_BY_INSTRUMENTATION) return
        markAsChild(testCase)
        onIgnored(testCase)
    }

    final override suspend fun testFinished(testCase: TestCase, result: TestResult) {
        if (result is TestResult.Ignored) return testIgnored(testCase, result.reason)
        val isTerminal = testCase.type != TestType.Container || !parents.remove(testCase.testPath)
        onFinished(testCase, result, isTerminal)
    }

    override suspend fun specFinished(ref: SpecRef, result: TestResult) {
        // A spec that blows up during instantiation, in beforeSpec or in afterSpec may not report a
        // test at all, so surface the failure against the spec itself.
        result.errorOrNull?.let { reportFailure(describeTest(specClass, SPEC_FAILURE_NAME), it) }
    }

    override suspend fun specIgnored(kclass: KClass<*>, reason: String?) {
        notifier.fireTestIgnored(describeSpec(specClass))
    }

    protected abstract fun onStarted(testCase: TestCase)

    protected abstract fun onIgnored(testCase: TestCase)

    /** [isTerminal] tells whether [testCase] had no children, i.e. whether it is a test in JUnit's eyes. */
    protected abstract fun onFinished(testCase: TestCase, result: TestResult, isTerminal: Boolean)

    protected fun describe(testPath: String): Description = describeTest(specClass, testPath)

    /** A complete started / failed / finished sequence, for a test that was not reported as started. */
    protected fun reportFailure(description: Description, error: Throwable) {
        notifier.fireTestStarted(description)
        notifier.fireTestFailure(Failure(description, error))
        notifier.fireTestFinished(description)
    }

    private fun markAsChild(testCase: TestCase) {
        testCase.parent?.let { parents += it.testPath }
    }

    protected companion object {
        fun TestResult.errorOrFailure(): Throwable? =
            if (isErrorOrFailure) errorOrNull ?: IllegalStateException("Test failed without an error") else null
    }
}

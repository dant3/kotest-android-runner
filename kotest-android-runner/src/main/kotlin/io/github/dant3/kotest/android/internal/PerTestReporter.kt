package io.github.dant3.kotest.android.internal

import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.engine.test.TestResult
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier

/**
 * Reports every Kotest test without children as a JUnit test of its own, its path flattened into
 * the method name — the report for a run that did not select tests by name.
 *
 * Leaves are reported as they start and finish. A childless container (a `withData` row) can only
 * be recognised as such once it finishes, so it is reported then. A container with children is
 * structure, not a result: it is only reported, under its own name, when it fails or is disabled
 * itself, so that such a container cannot vanish from the report.
 */
internal class PerTestReporter(
    notifier: RunNotifier,
    specClass: Class<*>,
) : AndroidTestEngineListener(notifier, specClass) {
    override fun onStarted(testCase: TestCase) {
        if (testCase.type != TestType.Container) notifier.fireTestStarted(describe(testCase.testPath))
    }

    override fun onIgnored(testCase: TestCase) {
        notifier.fireTestIgnored(describe(testCase.testPath))
    }

    override fun onFinished(testCase: TestCase, result: TestResult, isTerminal: Boolean) {
        val description = describe(testCase.testPath)
        val error = result.errorOrFailure()
        when {
            testCase.type != TestType.Container -> {
                error?.let { notifier.fireTestFailure(Failure(description, it)) }
                notifier.fireTestFinished(description)
            }
            error != null -> reportFailure(description, error)
            isTerminal -> {
                notifier.fireTestStarted(description)
                notifier.fireTestFinished(description)
            }
        }
    }
}

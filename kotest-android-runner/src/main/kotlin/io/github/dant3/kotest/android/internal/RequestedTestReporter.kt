package io.github.dant3.kotest.android.internal

import io.kotest.core.spec.SpecRef
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import org.junit.AssumptionViolatedException
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier

/**
 * Reports exactly one JUnit test per name selected through the `class` argument — the report for a
 * run that selected tests by name (see [TestRequest]).
 *
 * A selected leaf is reported as itself. A selected container runs in full and is reported as a
 * single test that fails if anything beneath it fails, listing every failure. A selected name that
 * never shows up is reported as a failure too, instead of silently passing.
 */
internal class RequestedTestReporter(
    notifier: RunNotifier,
    specClass: Class<*>,
    selected: List<String>,
) : AndroidTestEngineListener(notifier, specClass) {
    private val outcomes = selected.associateWith { Outcome(it) }

    /** Failed or disabled containers above a selected test, which may be why it never ran. */
    private val blockers = mutableMapOf<String, TestResult>()

    override fun onStarted(testCase: TestCase) {
        outcomes[testCase.testPath]?.let { notifier.fireTestStarted(describe(it.path)) }
    }

    override fun onIgnored(testCase: TestCase) {
        val path = testCase.testPath
        val covering = coveringOutcomes(path)
        if (covering.isEmpty()) blockers[path] = TestResult.Ignored(null)
        covering.forEach { outcome ->
            if (outcome.path == path) {
                notifier.fireTestIgnored(describe(path))
                outcome.reported = true
            } else {
                outcome.ignored++
            }
        }
    }

    override fun onFinished(testCase: TestCase, result: TestResult, isTerminal: Boolean) {
        val path = testCase.testPath
        val error = result.errorOrFailure()
        val covering = coveringOutcomes(path)
        if (covering.isEmpty() && error != null) blockers[path] = result
        covering.forEach { outcome ->
            error?.let { outcome.failures += path to it }
            if (outcome.path != path && isTerminal) outcome.executed++
        }
        outcomes[path]?.let(::finish)
    }

    override suspend fun specFinished(ref: SpecRef, result: TestResult) {
        val specError = result.errorOrNull
        val unreported = outcomes.values.filterNot { it.reported }
        unreported.forEach { outcome ->
            val description = describe(outcome.path)
            val blocker = blockers.entries.firstOrNull { (path, _) -> outcome.path.isAtOrUnder(path) }
            when {
                specError != null -> reportFailure(description, specError)
                blocker?.value is TestResult.Ignored -> notifier.fireTestIgnored(description)
                blocker != null -> reportFailure(
                    description,
                    AssertionError("'${blocker.key}' failed before '${outcome.path}' could run", blocker.value.errorOrNull),
                )
                else -> reportFailure(
                    description,
                    AssertionError(
                        "${specClass.name} has no test named '${outcome.path}'. Nested test names are the Kotest " +
                            "test path joined with '$TEST_PATH_SEPARATOR'.",
                    ),
                )
            }
        }
        // An error already attributed to the selected tests needs no report of its own.
        if (unreported.isEmpty()) super.specFinished(ref, result)
    }

    private fun coveringOutcomes(path: String): List<Outcome> = outcomes.values.filter { path.isAtOrUnder(it.path) }

    private fun finish(outcome: Outcome) {
        val description = describe(outcome.path)
        when {
            outcome.failures.isNotEmpty() -> notifier.fireTestFailure(Failure(description, outcome.error()))
            // Started, but nothing beneath it was enabled: JUnit's way of saying "skipped after all".
            outcome.executed == 0 && outcome.ignored > 0 -> notifier.fireTestAssumptionFailed(
                Failure(description, AssumptionViolatedException("every test under '${outcome.path}' is disabled")),
            )
        }
        notifier.fireTestFinished(description)
        outcome.reported = true
    }

    private class Outcome(val path: String) {
        val failures = mutableListOf<Pair<String, Throwable>>()
        var executed = 0
        var ignored = 0
        var reported = false

        fun error(): Throwable {
            failures.singleOrNull()?.let { (failedPath, error) -> if (failedPath == path) return error }
            val summary = failures.joinToString(
                separator = "\n",
                prefix = "${failures.size} failure(s) under '$path':\n",
            ) { (failedPath, error) -> "  $failedPath: ${error.message ?: error::class.java.name}" }
            return AssertionError(summary, failures.first().second).apply {
                failures.drop(1).forEach { (_, error) -> addSuppressed(error) }
            }
        }
    }
}

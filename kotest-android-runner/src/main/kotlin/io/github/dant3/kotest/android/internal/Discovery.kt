package io.github.dant3.kotest.android.internal

import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.common.KotestInternal
import io.kotest.core.spec.style.TestXMethod
import io.kotest.core.test.DefaultTestScope
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.engine.spec.Materializer
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout

/**
 * Whether this instrumentation only lists tests, for a tool that then runs each listed name in an
 * instrumentation of its own: Android Test Orchestrator (`listTestsForOrchestrator`), or a dry run
 * (`log`) as used by Marathon and similar test runners.
 */
internal fun isListingTests(): Boolean = runCatching {
    val arguments = InstrumentationRegistry.getArguments()
    LISTING_ARGUMENTS.any { arguments.getString(it).toBoolean() }
}.getOrDefault(false)

/**
 * Test paths to list for a tool that runs every listed name in isolation — so that a `withData`
 * table under a `context` is listed row by row, not as one test.
 *
 * Nested tests only exist once their container's body runs, so the bodies of plain containers
 * (`context`, `describe`, …) are run here against a scope that records what they register without
 * running any of it. Container bodies are registration code, just like the spec constructor that
 * discovery runs anyway. The expansion stops at:
 *
 * - leaves;
 * - data-test rows (`withData` & co.), recognised by the tag Kotest puts on them: a row's body is
 *   the test, so it must not run here;
 * - disabled containers, whose bodies must not run at all;
 * - containers whose body fails without the spec's lifecycle around it (state from `beforeSpec`,
 *   say), does not finish within [CONTAINER_TIMEOUT], registers nothing (it is a test itself, then),
 *   or registers duplicate names (which the engine renames in ways not worth replicating).
 *
 * Wherever it stops, that node is listed and, when run by name, reported as one test.
 */
@OptIn(KotestInternal::class)
internal suspend fun listTestPaths(roots: List<TestCase>): List<String> = roots.flatMap { expand(it) }

@OptIn(KotestInternal::class)
private suspend fun expand(testCase: TestCase): List<String> {
    if (!testCase.isExpandable()) return listOf(testCase.testPath)
    val materializer = Materializer()
    val children = mutableListOf<TestCase>()
    val registered = runCatching {
        val scope = DefaultTestScope(testCase) { nested ->
            check(children.none { it.name.name == nested.name.name }) { "duplicate test name ${nested.name.name}" }
            children += materializer.materialize(nested, testCase)
        }
        withTimeout(CONTAINER_TIMEOUT) { testCase.test(scope) }
    }
    if (registered.isFailure || children.isEmpty()) return listOf(testCase.testPath)
    return children.flatMap { expand(it) }
}

private fun TestCase.isExpandable(): Boolean {
    val config = config
    return type == TestType.Container &&
        xmethod != TestXMethod.DISABLED &&
        !name.bang &&
        config?.enabled != false &&
        config?.enabledIf == null &&
        config?.enabledOrReasonIf == null &&
        config?.tags.orEmpty().none { it.name == DATA_TEST_TAG }
}

private val CONTAINER_TIMEOUT = 10.seconds

private const val DATA_TEST_TAG = "kotest.data"

private val LISTING_ARGUMENTS = listOf("listTestsForOrchestrator", "log")

package io.github.dant3.kotest.android.internal

import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.common.KotestInternal
import io.kotest.core.names.DuplicateTestNameMode
import io.kotest.core.spec.style.TestXMethod
import io.kotest.core.test.DefaultTestScope
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.engine.config.SpecConfigResolver
import io.kotest.engine.names.UniqueNames
import io.kotest.engine.spec.Materializer
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
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
 * - containers that register nothing — such a container is a test itself;
 * - containers whose body fails without the spec's lifecycle around it (state from `beforeSpec`,
 *   say) or does not finish within [CONTAINER_TIMEOUT].
 *
 * Wherever it stops, that node is listed and, when run by name, reported as one test. For the last
 * case that means tests lose the isolation they would otherwise get, so it is logged with the
 * reason. Duplicate names are made unique exactly the way the engine does, so they are listed under
 * the names the tests will run with.
 */
@OptIn(KotestInternal::class)
internal suspend fun listTestPaths(roots: List<TestCase>): List<String> = roots.flatMap { expand(it) }

@OptIn(KotestInternal::class)
private suspend fun expand(testCase: TestCase): List<String> {
    if (!testCase.isExpandable()) return listOf(testCase.testPath)
    val materializer = Materializer()
    val duplicateNameMode = SpecConfigResolver().duplicateTestNameMode(testCase.spec)
    val children = mutableListOf<TestCase>()
    val registered = runCatching {
        val scope = DefaultTestScope(testCase) { nested ->
            val name = children.map { it.name.name }.toSet().uniqueName(nested.name.name, duplicateNameMode)
            children += materializer.materialize(nested.copy(name = nested.name.copy(name = name)), testCase)
        }
        withTimeout(CONTAINER_TIMEOUT) { testCase.test(scope) }
    }
    registered.exceptionOrNull()?.let { error ->
        val reason = when (error) {
            is TimeoutCancellationException -> "its body did not finish within $CONTAINER_TIMEOUT"
            is DuplicateTestNameError -> "${error.message}, which fails the run as well"
            else -> "its body failed outside the spec lifecycle (hooks such as beforeSpec do not run while listing): $error"
        }
        logWarning(
            "'${testCase.testPath}' in ${testCase.spec::class.java.name} is listed as one test, so the tests inside " +
                "it share one process and one result: $reason",
        )
        return listOf(testCase.testPath)
    }
    return if (children.isEmpty()) listOf(testCase.testPath) else children.flatMap { expand(it) }
}

/** What the engine's `DuplicateTestNameHandler` (internal to Kotest) makes of [name], given the names taken so far. */
private fun Set<String>.uniqueName(name: String, mode: DuplicateTestNameMode): String = when {
    name !in this -> name
    mode == DuplicateTestNameMode.Error -> throw DuplicateTestNameError(name)
    else -> UniqueNames.unique(name, this) ?: name
}

private class DuplicateTestNameError(name: String) :
    IllegalStateException("it registers the test name '$name' twice under DuplicateTestNameMode.Error")

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

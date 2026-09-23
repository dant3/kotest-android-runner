package io.github.dant3.kotest.android.internal

import androidx.test.platform.app.InstrumentationRegistry

/**
 * The tests of one spec that were selected by name through the instrumentation's `class` argument
 * (`-e class Spec#a -- b`) — which is what Gradle's `--tests`, Android Studio, Test Lab sharding and
 * Android Test Orchestrator all boil down to.
 *
 * A selection is a contract: every selected name is announced to JUnit up front and receives exactly
 * one result, whether it names a leaf or a whole container. The orchestrator in particular runs each
 * listed name in a process of its own and forwards only the first failure it sees, so reporting
 * anything other than the one requested test would lose results.
 */
internal data class TestRequest(
    /**
     * Names to announce in the runner's description. Besides [selected], this includes whatever
     * AndroidX's own tokenizer made of the argument: its method filter drops a runner none of whose
     * announced children it recognises, before the runner is ever asked to run.
     */
    val announced: List<String>,
    /** Test paths to run, each reported as a single test. */
    val selected: List<String>,
) {
    val isEmpty: Boolean get() = selected.isEmpty()

    companion object {
        /**
         * Returns an empty request when nothing was selected, or when there is no instrumentation to
         * ask — e.g. when the runner is exercised from a plain JVM test.
         */
        fun fromInstrumentation(specClass: Class<*>): TestRequest =
            forSpec(specClass, runCatching { InstrumentationRegistry.getArguments().getString(CLASS_ARGUMENT) }.getOrNull())

        fun forSpec(specClass: Class<*>, classArgument: String?): TestRequest {
            if (classArgument.isNullOrEmpty()) return TestRequest(emptyList(), emptyList())
            fun namesIn(args: List<TestArg>) = args.mapNotNull { arg ->
                arg.methodName?.takeIf { arg.className == specClass.name && it.isNotEmpty() }
            }
            val tokenized = namesIn(parseClassArgument(classArgument))
            val selected = namesIn(parseClassArgument(classArgument) { it.isLoadableClass(specClass.classLoader) })
            return TestRequest(announced = (tokenized + selected).distinct(), selected = selected.distinct())
        }
    }
}

internal data class TestArg(val className: String, val methodName: String?)

/**
 * Splits a `class` argument into `class` / `class#method` entries the way AndroidX's
 * `ClassesArgTokenizer` does: entries are separated by commas, except inside `[…]` and `(…)`.
 *
 * AndroidX splits on *every* other comma, which cuts a Kotest name like `row 1, 2` in two. Given
 * [startsEntry], a comma inside a method name only separates entries when the text after it starts
 * one — i.e. names a class — so such a name survives intact.
 */
internal fun parseClassArgument(input: String, startsEntry: (className: String) -> Boolean = { true }): List<TestArg> {
    val args = mutableListOf<TestArg>()
    var pos = 0
    while (pos < input.length) {
        val classEnd = input.classNameEnd(pos)
        val className = input.substring(pos, classEnd)
        if (classEnd < input.length && input[classEnd] == '#') {
            val methodEnd = input.methodNameEnd(classEnd + 1, startsEntry)
            args += TestArg(className, input.substring(classEnd + 1, methodEnd))
            pos = methodEnd + 1
        } else {
            args += TestArg(className, null)
            pos = classEnd + 1
        }
    }
    return args
}

private fun String.classNameEnd(from: Int): Int = indexOfAny(charArrayOf('#', ','), from).takeIf { it >= 0 } ?: length

private fun String.methodNameEnd(from: Int, startsEntry: (String) -> Boolean): Int {
    var pos = from
    while (pos < length) {
        when (this[pos]) {
            ',' -> if (startsEntry(substring(pos + 1, classNameEnd(pos + 1)))) return pos
            '[' -> pos = indexOf(']', pos).takeIf { it > 0 } ?: return length
            '(' -> pos = indexOf(')', pos).takeIf { it > 0 } ?: return length
        }
        pos++
    }
    return pos
}

private fun String.isLoadableClass(classLoader: ClassLoader?): Boolean =
    runCatching { Class.forName(this, false, classLoader) }.isSuccess

private const val CLASS_ARGUMENT = "class"

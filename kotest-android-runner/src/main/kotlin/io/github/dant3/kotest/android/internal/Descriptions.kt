package io.github.dant3.kotest.android.internal

import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.core.descriptors.Descriptor
import io.kotest.core.test.TestCase
import org.junit.runner.Description

/**
 * Separator between nested test names when a Kotest test path is flattened into the single
 * "method name" slot that JUnit 4 (and therefore `am instrument`) gives us.
 */
internal const val TEST_PATH_SEPARATOR: String = " -- "

internal fun Descriptor.TestDescriptor.testPath(): String = testParts().joinToString(TEST_PATH_SEPARATOR)

internal fun describeSpec(specClass: Class<*>): Description = Description.createSuiteDescription(specClass)

/**
 * The flattened test path doubles as JUnit's unique id, so a description built up front (from
 * [requestedTestNames] or from the root tests) equals the one reported once the test actually
 * runs — which is what lets JUnit reconcile the two.
 */
internal fun describeTest(specClass: Class<*>, testPath: String): Description =
    Description.createTestDescription(specClass.name, testPath, testPath)

internal fun describeTest(specClass: Class<*>, descriptor: Descriptor.TestDescriptor): Description =
    describeTest(specClass, descriptor.testPath())

internal fun describeTest(testCase: TestCase): Description = describeTest(testCase.spec::class.java, testCase.descriptor)

/**
 * Test names selected through the instrumentation's `class` argument (`-e class Spec#a -- b`,
 * which is what Gradle's `--tests`, Android Studio and Test Lab all go through) for [specClass].
 *
 * Returns an empty list when nothing was selected, or when there is no instrumentation to ask —
 * e.g. when the runner is exercised from a plain JVM test.
 */
internal fun requestedTestNames(specClass: Class<*>): List<String> = runCatching {
    InstrumentationRegistry.getArguments().getString(CLASS_ARGUMENT).orEmpty()
}.getOrDefault("")
    .split(',')
    .mapNotNull { entry ->
        val (className, testName) = entry.trim().split('#', limit = 2).let { it.first() to it.getOrNull(1) }
        testName?.takeIf { className == specClass.name && it.isNotEmpty() }
    }

private const val CLASS_ARGUMENT = "class"

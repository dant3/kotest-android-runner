package io.github.dant3.kotest.android.internal

import io.kotest.core.descriptors.Descriptor
import io.kotest.core.test.TestCase
import org.junit.runner.Description

/**
 * Separator between nested test names when a Kotest test path is flattened into the single
 * "method name" slot that JUnit 4 (and therefore `am instrument`) gives us.
 */
internal const val TEST_PATH_SEPARATOR: String = " -- "

/** The name a spec-level failure (instantiation, `beforeSpec`, `afterSpec`) is reported under. */
internal const val SPEC_FAILURE_NAME: String = "spec initialization"

internal fun Descriptor.TestDescriptor.testPath(): String = testParts().joinToString(TEST_PATH_SEPARATOR)

internal val TestCase.testPath: String get() = descriptor.testPath()

/** Whether this test path is [other] itself or a test nested somewhere beneath it. */
internal fun String.isAtOrUnder(other: String): Boolean = this == other || startsWith(other + TEST_PATH_SEPARATOR)

internal fun describeSpec(specClass: Class<*>): Description = Description.createSuiteDescription(specClass)

/**
 * The flattened test path doubles as JUnit's unique id, so a description built up front (from a
 * [TestRequest] or from the root tests) equals the one reported once the test actually runs —
 * which is what lets JUnit reconcile the two.
 */
internal fun describeTest(specClass: Class<*>, testPath: String): Description =
    Description.createTestDescription(specClass.name, testPath, testPath)

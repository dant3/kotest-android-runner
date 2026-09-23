package io.github.dant3.kotest.android.e2e

import io.github.dant3.kotest.android.KotestAndroidRunner
import io.github.dant3.kotest.android.targetContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.junit.runner.RunWith

/**
 * In Kotest 6 every `withData` row is a container — its body is the test. Rows are reported as tests
 * of their own (`parity -- 2`), or, when a run selects a container by name, as part of it.
 */
@RunWith(KotestAndroidRunner::class)
class DataTestsTest : FunSpec({

    withData(nameFn = { "package name segment $it" }, 0, 1) { index ->
        targetContext.packageName.split('.')[index] shouldBe listOf("io", "github")[index]
    }

    context("parity") {
        withData(2, 4, 6) { number ->
            number % 2 shouldBe 0
        }
    }
})

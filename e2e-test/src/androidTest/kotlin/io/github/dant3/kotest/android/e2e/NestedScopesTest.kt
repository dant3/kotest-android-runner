package io.github.dant3.kotest.android.e2e

import io.github.dant3.kotest.android.KotestAndroidRunner
import io.github.dant3.kotest.android.targetContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import org.junit.runner.RunWith

/**
 * Nested Kotest scopes have no JUnit 4 equivalent: they are flattened into the reported test
 * name, joined with " -- " (e.g. `nested scopes -- deeper -- reports every leaf`).
 */
@RunWith(KotestAndroidRunner::class)
class NestedScopesTest : FunSpec({

    context("nested scopes") {
        test("reports a leaf") {
            targetContext shouldNotBe null
        }

        context("deeper") {
            test("reports every leaf") {
                targetContext shouldNotBe null
            }
        }
    }
})

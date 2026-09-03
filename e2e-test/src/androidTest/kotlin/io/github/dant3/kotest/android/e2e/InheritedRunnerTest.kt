package io.github.dant3.kotest.android.e2e

import io.github.dant3.kotest.android.KotestAndroidRunner
import io.github.dant3.kotest.android.targetContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import org.junit.runner.RunWith

/**
 * `@RunWith` is `@Inherited`, so a project that does not want to repeat the annotation can declare
 * its own annotated base spec once. This is the supported replacement for shipping pre-annotated
 * spec styles in the library — a meta-annotation would not work, since JUnit 4 looks for `@RunWith`
 * on the class itself and never through other annotations.
 */
@RunWith(KotestAndroidRunner::class)
abstract class AndroidFunSpec(body: FunSpec.() -> Unit = {}) : FunSpec(body)

class InheritedRunnerTest : AndroidFunSpec({

    test("a subclass of an annotated base spec is picked up") {
        targetContext shouldNotBe null
    }
})

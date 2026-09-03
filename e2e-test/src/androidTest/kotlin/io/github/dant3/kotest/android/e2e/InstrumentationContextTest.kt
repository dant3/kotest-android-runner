package io.github.dant3.kotest.android.e2e

import android.os.Build
import io.github.dant3.kotest.android.FunSpec
import io.github.dant3.kotest.android.instrumentation
import io.github.dant3.kotest.android.targetContext
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** The baseline: a Kotest spec really executes inside the instrumentation, on the device. */
class InstrumentationContextTest : FunSpec({

    test("runs against the real Android framework") {
        Build.VERSION.SDK_INT shouldBeGreaterThan 0
        Build.FINGERPRINT shouldNotBe null
    }

    test("target context belongs to the module under test") {
        targetContext.packageName shouldBe "io.github.dant3.kotest.android.e2e.test"
    }

    test("instrumentation is registered") {
        instrumentation shouldNotBe null
    }
})

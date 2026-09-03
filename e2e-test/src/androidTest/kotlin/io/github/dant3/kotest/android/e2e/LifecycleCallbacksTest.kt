package io.github.dant3.kotest.android.e2e

import io.github.dant3.kotest.android.KotestAndroidRunner
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.junit.runner.RunWith

/** Kotest lifecycle callbacks must fire on device exactly as they do on the JVM. */
@RunWith(KotestAndroidRunner::class)
class LifecycleCallbacksTest : FunSpec({

    val events = mutableListOf<String>()

    beforeSpec { events += "beforeSpec" }
    beforeTest { events += "beforeTest" }
    afterTest { events += "afterTest" }

    test("first test sees beforeSpec and beforeTest") {
        events shouldBe listOf("beforeSpec", "beforeTest")
    }

    test("second test sees the first test's afterTest") {
        events shouldBe listOf("beforeSpec", "beforeTest", "afterTest", "beforeTest")
    }
})

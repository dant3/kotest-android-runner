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

    // Every test runs on its own under the orchestrator, so none may rely on another having run.
    test("beforeSpec and beforeTest run before the test") {
        events.take(2) shouldBe listOf("beforeSpec", "beforeTest")
        events.last() shouldBe "beforeTest"
    }

    test("afterTest runs after each test") {
        events.count { it == "afterTest" } shouldBe events.count { it == "beforeTest" } - 1
    }
})

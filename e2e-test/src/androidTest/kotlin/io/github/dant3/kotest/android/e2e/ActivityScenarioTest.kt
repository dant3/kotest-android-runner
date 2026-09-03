package io.github.dant3.kotest.android.e2e

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import io.github.dant3.kotest.android.KotestAndroidRunner
import io.github.dant3.kotest.android.onMainThread
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.junit.runner.RunWith

/** Real UI: launching an activity and reading its view state from the main thread. */
@RunWith(KotestAndroidRunner::class)
class ActivityScenarioTest : FunSpec({

    test("launches an activity and reads its label") {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.label.text.toString() shouldBe TestActivity.GREETING
            }
        }
    }

    test("onMainThread dispatches to the main looper") {
        val text = onMainThread {
            TextView(io.github.dant3.kotest.android.targetContext).apply { text = "made on main" }.text.toString()
        }
        text shouldBe "made on main"
    }
})

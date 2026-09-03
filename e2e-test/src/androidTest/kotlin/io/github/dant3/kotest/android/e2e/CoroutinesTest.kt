package io.github.dant3.kotest.android.e2e

import io.github.dant3.kotest.android.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Kotest test bodies are suspend functions; the Android dispatchers must work inside them. */
class CoroutinesTest : FunSpec({

    test("suspends and resumes") {
        delay(10)
        withContext(Dispatchers.Default) { 21 * 2 } shouldBe 42
    }
})

package io.github.dant3.kotest.android.e2e.contract

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

// Specs that ReportingContractTest runs through the runner and inspects. They deliberately carry
// no @RunWith — several of them fail on purpose — so AndroidJUnitRunner never picks them up itself.

/** Counts test body executions, so a test can tell "reported" from "actually ran". */
object Executions {
    private val counts = mutableMapOf<String, Int>()

    fun record(name: String) {
        synchronized(counts) { counts[name] = (counts[name] ?: 0) + 1 }
    }

    fun reset() = synchronized(counts) { counts.clear() }

    fun of(name: String): Int = synchronized(counts) { counts[name] ?: 0 }
}

class ContainerOnlySpec : FunSpec({
    context("the only root node is a container") {
        test("its leaf") {
            Executions.record("its leaf")
            1 shouldBe 2
        }
    }
})

class PassingTableSpec : FunSpec({
    withData(nameFn = { "row $it" }, 1, 2) {
        Executions.record("row $it")
        it shouldBe it
    }
})

class FailingTableInContextSpec : FunSpec({
    context("ctx") {
        withData(nameFn = { "row $it" }, 1, 2) {
            Executions.record("row $it")
            it shouldBe 0
        }
    }
})

class CommaSpec : FunSpec({
    withData(nameFn = { "a table, row $it" }, 1, 2) {
        Executions.record("a table, row $it")
        it shouldBe 1
    }
})

class MixedSpec : FunSpec({
    test("root leaf") {
        Executions.record("root leaf")
    }

    context("group") {
        test("passes") {
            Executions.record("passes")
        }
        xtest("disabled") {
            Executions.record("disabled")
        }
    }

    context("all disabled") {
        xtest("nothing runs") {
            Executions.record("nothing runs")
        }
    }
})

class ContainerFailingEarlySpec : FunSpec({
    context("broken") {
        error("boom before any test is registered")
    }
})

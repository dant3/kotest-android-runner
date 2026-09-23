package io.github.dant3.kotest.android.e2e.contract

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.dant3.kotest.android.KotestAndroidRunner
import io.github.dant3.kotest.android.instrumentation
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.reflect.KClass
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.RunWith
import org.junit.runner.manipulation.Filter
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * Pins down what JUnit is told about each probe spec: the events are what AndroidJUnitRunner turns
 * into `Tests run` counts and XML reports, so a spec whose tests silently vanish fails here rather
 * than staying green.
 */
@RunWith(KotestAndroidRunner::class)
class ReportingContractTest : FunSpec({

    beforeTest { Executions.reset() }

    context("a plain run reports every test without children") {
        test("a spec whose only root is a container runs and reports its leaf") {
            val run = run(ContainerOnlySpec::class)
            run.announced shouldContainExactly listOf("the only root node is a container")
            run.events shouldContainExactly listOf(
                "started: the only root node is a container -- its leaf",
                "failed: the only root node is a container -- its leaf",
                "finished: the only root node is a container -- its leaf",
            )
            Executions.of("its leaf") shouldBe 1
        }

        test("passing withData rows are reported, they are tests even though Kotest calls them containers") {
            run(PassingTableSpec::class).events shouldContainExactly listOf(
                "started: row 1", "finished: row 1",
                "started: row 2", "finished: row 2",
            )
        }

        test("every failing row is reported") {
            run(FailingTableInContextSpec::class).failed shouldContainExactly listOf("ctx -- row 1", "ctx -- row 2")
        }

        test("disabled tests are reported as ignored, and a container of disabled tests is not a test") {
            val run = run(MixedSpec::class)
            run.passed shouldContainExactly listOf("root leaf", "group -- passes")
            run.ignored shouldContainExactlyInAnyOrder listOf("group -- disabled", "all disabled -- nothing runs")
            Executions.of("disabled") shouldBe 0
        }

        test("a container that fails before registering a test is reported under its own name") {
            run(ContainerFailingEarlySpec::class).failed shouldContainExactly listOf("broken")
        }

        test("a JUnit filter, e.g. sharding, selects among the announced root tests") {
            val onlyGroup = object : Filter() {
                override fun shouldRun(description: Description) = !description.isTest || description.methodName == "group"
                override fun describe() = "only group"
            }
            run(MixedSpec::class, filter = onlyGroup).passed shouldContainExactly listOf("group -- passes")
            Executions.of("root leaf") shouldBe 0
        }
    }

    context("a run that selects tests by name reports exactly the selected names") {
        test("a selected container runs in full and is reported once, with every failure") {
            val run = run(FailingTableInContextSpec::class, "ctx")
            run.events shouldContainExactly listOf("started: ctx", "failed: ctx", "finished: ctx")
            run.failures.single().message shouldContain "ctx -- row 1"
            run.failures.single().message shouldContain "ctx -- row 2"
            Executions.of("row 1") shouldBe 1
            Executions.of("row 2") shouldBe 1
        }

        test("a spec whose only root is a container reports it when selected, the way the orchestrator asks") {
            run(ContainerOnlySpec::class, "the only root node is a container").failed shouldContainExactly
                listOf("the only root node is a container")
            Executions.of("its leaf") shouldBe 1
        }

        test("a selected nested row runs alone, its siblings do not") {
            run(FailingTableInContextSpec::class, "ctx -- row 2").failed shouldContainExactly listOf("ctx -- row 2")
            Executions.of("row 1") shouldBe 0
            Executions.of("row 2") shouldBe 1
        }

        test("a name containing a comma is selected whole") {
            val run = run(CommaSpec::class, "a table, row 2")
            run.failed shouldContainExactly listOf("a table, row 2")
            Executions.of("a table, row 1") shouldBe 0
        }

        test("a passing selected container is reported as passed") {
            run(MixedSpec::class, "group").passed shouldContainExactly listOf("group")
            Executions.of("root leaf") shouldBe 0
        }

        test("a selected container whose tests are all disabled is reported as skipped") {
            run(MixedSpec::class, "all disabled").events shouldContainExactly listOf(
                "started: all disabled", "skipped: all disabled", "finished: all disabled",
            )
        }

        test("several selected names each get their own result") {
            val spec = PassingTableSpec::class.java.name
            run(PassingTableSpec::class, classArgument = "$spec#row 1,$spec#row 2").passed shouldContainExactly
                listOf("row 1", "row 2")
        }

        test("a name that does not exist fails instead of passing silently") {
            val run = run(MixedSpec::class, "no such test")
            run.failed shouldContainExactly listOf("no such test")
            run.failures.single().message shouldContain "has no test named 'no such test'"
        }

        test("a test under a container that failed early reports that failure") {
            val run = run(ContainerFailingEarlySpec::class, "broken -- anything")
            run.failed shouldContainExactly listOf("broken -- anything")
            run.failures.single().message shouldContain "'broken' failed"
        }
    }
})

private class RecordedRun(val announced: List<String>, val events: List<String>, val failures: List<Failure>) {
    private fun named(kind: String) = events.filter { it.startsWith("$kind: ") }.map { it.removePrefix("$kind: ") }

    val failed get() = named("failed")
    val ignored get() = named("ignored")
    val passed get() = named("finished") - failed.toSet() - named("skipped").toSet()
}

/**
 * Runs [spec] the way AndroidJUnitRunner would: with [selected] (or a raw [classArgument]) standing
 * in for `-e class Spec#name`, and [filter] for the filters it applies.
 */
private fun run(
    spec: KClass<out Spec>,
    selected: String? = null,
    classArgument: String? = selected?.let { "${spec.java.name}#$it" },
    filter: Filter? = null,
): RecordedRun = withClassArgument(classArgument) {
    val runner = KotestAndroidRunner(spec.java)
    val announced = runner.description.children.map { it.methodName }
    val events = mutableListOf<String>()
    val failures = mutableListOf<Failure>()
    val core = JUnitCore()
    core.addListener(object : RunListener() {
        override fun testStarted(description: Description) { events += "started: ${description.methodName}" }
        override fun testFinished(description: Description) { events += "finished: ${description.methodName}" }
        override fun testIgnored(description: Description) { events += "ignored: ${description.methodName}" }
        override fun testFailure(failure: Failure) {
            events += "failed: ${failure.description.methodName}"
            failures += failure
        }
        override fun testAssumptionFailure(failure: Failure) { events += "skipped: ${failure.description.methodName}" }
    })
    val request = Request.runner(runner).let { if (filter != null) it.filterWith(filter) else it }
    core.run(request)
    RecordedRun(announced, events, failures)
}

private fun <T> withClassArgument(classArgument: String?, block: () -> T): T {
    val original = InstrumentationRegistry.getArguments()
    val arguments = Bundle(original).apply {
        if (classArgument == null) remove("class") else putString("class", classArgument)
    }
    InstrumentationRegistry.registerInstance(instrumentation, arguments)
    try {
        return block()
    } finally {
        InstrumentationRegistry.registerInstance(instrumentation, original)
    }
}

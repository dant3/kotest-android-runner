package io.github.dant3.kotest.android

import io.github.dant3.kotest.android.internal.PerTestReporter
import io.github.dant3.kotest.android.internal.RequestedTestReporter
import io.github.dant3.kotest.android.internal.SPEC_FAILURE_NAME
import io.github.dant3.kotest.android.internal.TestRequest
import io.github.dant3.kotest.android.internal.TestSelection
import io.github.dant3.kotest.android.internal.describeSpec
import io.github.dant3.kotest.android.internal.describeTest
import io.github.dant3.kotest.android.internal.testPath
import io.kotest.common.KotestInternal
import io.kotest.core.spec.Spec
import io.kotest.core.spec.SpecRef
import io.kotest.engine.TestEngineLauncher
import io.kotest.engine.config.ProjectConfigResolver
import io.kotest.engine.extensions.DefaultExtensionRegistry
import io.kotest.engine.spec.Materializer
import io.kotest.engine.spec.SpecInstantiator
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.notification.RunNotifier

/**
 * JUnit 4 runner that executes a [Spec] with the Kotest engine, on a device or emulator, under
 * `AndroidJUnitRunner`.
 *
 * Annotate a spec with `@RunWith(KotestAndroidRunner::class)`.
 *
 * Kotest tests without children — leaves, and containers that register nothing, such as `withData`
 * rows — are reported to JUnit as tests: nested Kotest scopes are flattened into the JUnit "method
 * name" slot, joined with ` -- `. That flattened name is also what `-e class` / `--tests` filters
 * match against; a name selected that way is reported as exactly one test, even when it names a
 * container.
 */
@OptIn(KotestInternal::class)
public class KotestAndroidRunner(
    private val specClass: Class<out Spec>,
) : Runner(),
    Filterable {
    private val specRef = SpecRef.Reference(specClass.kotlin)
    private val request = TestRequest.fromInstrumentation(specClass)
    private var filter: Filter? = null

    // JUnit asks for the description many times over, and building it instantiates the spec.
    private val cachedDescription: Description by lazy {
        val description = describeSpec(specClass)
        // `-e class Spec#a -- b` can select a nested test, which cannot be enumerated without running
        // the spec. JUnit drops a runner whose description has no matching child *before* it ever
        // calls `filter`, so selected names are announced as-is and reconciled at run time.
        // Otherwise the root tests are announced — the most that is known before running: whether
        // a container holds tests or is a test (a `withData` row) only shows once its body runs.
        val names = request.announced.ifEmpty { rootTestPaths() }
        names.forEach { description.addChild(describeTest(specClass, it)) }
        description
    }

    override fun getDescription(): Description = cachedDescription

    override fun filter(filter: Filter) {
        this.filter = filter
    }

    override fun run(notifier: RunNotifier) {
        val (reporter, selection) = if (request.isEmpty) {
            PerTestReporter(notifier, specClass) to filter?.let { TestSelection.of(it, specClass) }
        } else {
            RequestedTestReporter(notifier, specClass, request.selected) to TestSelection.of(request)
        }
        runBlocking {
            var launcher = TestEngineLauncher()
                .withListener(reporter)
                .withSpecRefs(specRef)
            selection?.let { launcher = launcher.addExtension(it) }
            launcher.execute()
        }
    }

    /**
     * A spec that cannot even be instantiated is announced under the name its failure will be
     * reported with once it runs, so the failure is not lost to the discovery step.
     */
    private fun rootTestPaths(): List<String> = runBlocking {
        val spec = SpecInstantiator(DefaultExtensionRegistry(), ProjectConfigResolver())
            .createAndInitializeSpec(specClass.kotlin)
            .getOrElse { return@runBlocking listOf(SPEC_FAILURE_NAME) }
        Materializer().materialize(spec, specRef).map { it.descriptor.testPath() }
    }
}

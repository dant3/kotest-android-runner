package io.github.dant3.kotest.android

import io.github.dant3.kotest.android.internal.AndroidTestEngineListener
import io.github.dant3.kotest.android.internal.InstrumentationTestFilter
import io.github.dant3.kotest.android.internal.describeSpec
import io.github.dant3.kotest.android.internal.describeTest
import io.github.dant3.kotest.android.internal.requestedTestNames
import io.github.dant3.kotest.android.internal.testPath
import io.kotest.common.KotestInternal
import io.kotest.core.spec.Spec
import io.kotest.core.spec.SpecRef
import io.kotest.core.test.TestCase
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
 * Annotate a spec with `@RunWith(KotestAndroidRunner::class)`, or extend one of the base specs
 * in [io.github.dant3.kotest.android] which carry the annotation already.
 *
 * Only leaf tests are reported to JUnit: nested Kotest scopes are flattened into the JUnit
 * "method name" slot, joined with ` -- `. That flattened name is also what `-e class` /
 * `--tests` filters match against.
 */
@OptIn(KotestInternal::class)
public class KotestAndroidRunner(
    private val specClass: Class<out Spec>,
) : Runner(),
    Filterable {
    private val specRef = SpecRef.Reference(specClass.kotlin)
    private var filter: Filter? = null

    override fun getDescription(): Description {
        val description = describeSpec(specClass)
        // `-e class Spec#a -- b` (what Gradle's `--tests`, Android Studio and Test Lab sharding
        // all boil down to) can select a nested test, which cannot be enumerated without running
        // the spec. JUnit drops a runner whose description has no matching child *before* it ever
        // calls `filter`, so selected names are announced as-is and reconciled at run time.
        // Otherwise only root tests are announced; nested ones are attached to the notifier as
        // the spec executes and discovers them.
        val names = requestedTestNames(specClass).ifEmpty { rootTests().map { it.descriptor.testPath() } }
        names.forEach { description.addChild(describeTest(specClass, it)) }
        return description
    }

    override fun filter(filter: Filter) {
        this.filter = filter
    }

    override fun run(notifier: RunNotifier) {
        runBlocking {
            var launcher = TestEngineLauncher()
                .withListener(AndroidTestEngineListener(notifier, specClass))
                .withSpecRefs(specRef)
            filter?.let { launcher = launcher.addExtension(InstrumentationTestFilter(it)) }
            launcher.execute()
        }
    }

    private fun rootTests(): List<TestCase> = runBlocking {
        val spec = SpecInstantiator(DefaultExtensionRegistry(), ProjectConfigResolver())
            .createAndInitializeSpec(specClass.kotlin)
            .getOrThrow()
        Materializer().materialize(spec, specRef)
    }
}

# kotest-android-runner

[![JitPack](https://jitpack.io/v/dant3/kotest-android-runner.svg)](https://jitpack.io/#dant3/kotest-android-runner)
[![Kotest](https://img.shields.io/badge/Kotest-6.2.4-blue?logo=kotlin)](https://kotest.io/)
[![AndroidX Test](https://img.shields.io/badge/AndroidX%20Test-1.7.0-green?logo=android)](https://developer.android.com/training/testing)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey)](LICENSE)

A JUnit 4 runner that executes [Kotest](https://kotest.io/) specs as **Android instrumented tests** — the
ones that live in `src/androidTest` and run on a real device or emulator under `AndroidJUnitRunner`.

It is the on-device counterpart to
[kotest-robolectric-extension](https://github.com/dant3/kotest-robolectric-extension), which runs Android
code on the JVM inside a Robolectric sandbox.

```kotlin
@RunWith(KotestAndroidRunner::class)
class ContextTest : FunSpec({
    test("runs on the device, against the real framework") {
        targetContext.packageName shouldBe "com.example.app"
    }
})
```

**Contents:**

- [Installation](#installation)
- [Quick start](#quick-start)
- [How Kotest tests map onto JUnit 4](#how-kotest-tests-map-onto-junit-4)
- [Running a single test](#running-a-single-test)
- [Android Test Orchestrator](#android-test-orchestrator)
- [Instrumentation helpers](#instrumentation-helpers)
- [What comes with the dependency](#what-comes-with-the-dependency)
- [Minification](#minification)
- [Troubleshooting](#troubleshooting)
- [Current limitations](#current-limitations)
- [Development](#development)
- [Reference projects](#reference-projects)
- [License](#license)

## Installation

The library is published through [JitPack](https://jitpack.io/#dant3/kotest-android-runner), so the
JitPack repository has to be declared first.

**`settings.gradle.kts`**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

<details>
<summary>Older projects that still declare repositories per-module</summary>

```kotlin
// build.gradle.kts of the consuming module
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

</details>

**`build.gradle.kts`** of the module whose `androidTest` sources you want to run:

```kotlin
android {
    defaultConfig {
        // The stock AndroidX runner is what discovers and drives the specs.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation("com.github.dant3:kotest-android-runner:1.2.0")
}
```

That single line is enough: Kotest's engine and assertions, JUnit 4 and `androidx.test:runner` come
along transitively — see [What comes with the dependency](#what-comes-with-the-dependency).

**Version catalog** (`gradle/libs.versions.toml`):

```toml
[libraries]
kotest-android-runner = { module = "com.github.dant3:kotest-android-runner", version = "1.2.0" }
```

The version slot accepts any of:

| Value | Meaning |
|---|---|
| `1.2.0` | a release tag — pinned, reproducible, recommended |
| `abc1234` | a short commit SHA — pinned to a specific commit |
| `main-SNAPSHOT` | latest commit on `main` — handy for trying unreleased fixes, not for CI |

> **Note on coordinates.** The group is `com.github.dant3` and the artifact is the repository name —
> **not** `com.github.dant3.kotest-android-runner:runner`. JitPack flattens the published submodule into
> a single repo-level artifact, so submodule-style coordinates return 404. If a build ever fails to
> resolve, the authoritative snippet is always on the
> [JitPack page](https://jitpack.io/#dant3/kotest-android-runner) under the version's *"Get it"* button.

Requires JDK 17+ to consume, `minSdk 23`, and Kotest 6.x. A release is built by JitPack on first
request, so the very first resolution of a new tag can take a couple of minutes.

## Quick start

Write an ordinary Kotest spec — any style — and point JUnit at the runner:

```kotlin
import io.github.dant3.kotest.android.KotestAndroidRunner
import io.github.dant3.kotest.android.targetContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.junit.runner.RunWith

@RunWith(KotestAndroidRunner::class)
class ContextTest : FunSpec({
    test("runs on the device, against the real framework") {
        targetContext.packageName shouldBe "com.example.app"
    }
})
```

Every Kotest style works — `FunSpec`, `StringSpec`, `ShouldSpec`, `DescribeSpec`, `BehaviorSpec`,
`FreeSpec`, `WordSpec`, `FeatureSpec`, `ExpectSpec` — the library adds no spec classes of its own.

> **Why one annotation per spec, and not a single `@AndroidTest` meta-annotation?** JUnit 4 does not
> look for `@RunWith` through other annotations — `AnnotatedBuilder` calls `getAnnotation(RunWith.class)`
> on the test class itself, so an annotation that merely carries `@RunWith` fails discovery with
> `Invalid test class: No test methods found`. Inheritance does work (`@RunWith` is `@Inherited`), so a
> project that really wants to drop the per-spec annotation can extend its own annotated base spec —
> at the cost of hiding the runner one level away from the test.

Then run them the usual way — no extra Gradle wiring:

```bash
./gradlew connectedDebugAndroidTest
```

Kotest lifecycle callbacks (`beforeSpec`, `beforeTest`, `afterTest`, `afterSpec`), coroutine test bodies
and `xtest`/`config(enabled = …)` behave exactly as they do on the JVM.

## How Kotest tests map onto JUnit 4

JUnit 4 has a flat `class#method` model, while Kotest tests form a tree. Nested scopes are therefore
**flattened into the reported method name**, joined with ` -- `:

```kotlin
@RunWith(KotestAndroidRunner::class)
class NestedScopesTest : FunSpec({
    context("nested scopes") {
        context("deeper") {
            test("reports every leaf") { /* ... */ }
        }
    }
})
```

is reported as:

```
io.example.NestedScopesTest > nested scopes -- deeper -- reports every leaf
```

Rules of the mapping:

- **Every test without children is reported as a test.** That is every leaf, and also every container
  that registers nothing — which is what a `withData` row is in Kotest 6: `withData` creates containers,
  and the row's body is the test (`parity -- 2`). Such a row is only recognised once it finishes, so it is
  reported then, with no meaningful duration of its own.
- **Containers with children are structure, not results.** A container that fails or is disabled
  itself — an exception in its body, a failing `afterContainer`, `xcontext` — is reported under its own
  name, so it cannot silently vanish from the report.
- **A spec that fails during instantiation or `beforeSpec`** is reported as `spec initialization`.
- **Disabled tests** (`xtest`, `config(enabled = false)`) are reported as ignored, not as passes.

## Running a single test

The flattened name is what test filters match against, so the usual tooling works:

```bash
# whole spec
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.example.NestedScopesTest

# a single nested test — mind the quoting, the name contains spaces
./gradlew connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=io.example.NestedScopesTest#nested scopes -- deeper -- reports every leaf"

# or straight through adb
adb shell "am instrument -w -e class 'io.example.NestedScopesTest#nested scopes -- deeper -- reports every leaf' \
  com.example.app.test/androidx.test.runner.AndroidJUnitRunner"
```

**A selected name is reported as exactly one test**, whatever it names:

- a test — it runs on its own; sibling tests (and sibling `withData` rows) do not run;
- a container — everything in it runs, and it is reported as a single test that fails if anything inside
  fails, with every failure listed in the message (`nested scopes -- deeper`);
- nothing — a typo, or a name that no longer exists — it fails with `… has no test named '…'` rather than
  passing with zero tests.

Nested tests only come into existence once a spec runs, and JUnit discards a runner whose description
has no matching child *before* it ever calls `filter`. The runner therefore reads the instrumentation's
`class` argument and announces the selected names up front, then reconciles them at run time.

AndroidJUnitRunner splits the `class` argument on commas, so a name like `row 1, 2` reaches the runner
in pieces. The runner reassembles it — a comma only separates entries when a class name follows it — but
AndroidJUnitRunner still tries to load the tail (` 2`) as a class and reports an `initializationError`
for it. Keep bare commas out of test names you intend to select by hand; under the orchestrator, which
discards such errors, they work as they are.

## Android Test Orchestrator

Supported, including `clearPackageData`:

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }
    testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"
}

dependencies {
    androidTestUtil("androidx.test:orchestrator:1.6.1")
}
```

The orchestrator lists the tests first, then runs each listed name in a process of its own, selected by
name as described [above](#running-a-single-test). **Every test and every `withData` row gets a process
and a line in the report of its own**, however deeply it is nested:

```kotlin
@RunWith(KotestAndroidRunner::class)
class TransliterationTest : FunSpec({
    context("transliteration") {
        withData("Мария", "Пётр", "Юлия") { name ->   // three tests, three processes
            // ...
        }
    }
})
```

Nested tests only exist once their container's body has run, so to list them the runner runs the bodies
of plain containers (`context`, `describe`, `given`, …) during listing, against a scope that records what
they register without running any of it — the same thing that already happens to the spec's constructor.
`withData` rows are recognised by the tag Kotest puts on them and are never run while listing: a row's body
is the test. What that means in practice:

- **Container bodies should only register tests.** One that does more — writes a file, starts an
  activity — does it once more, in the listing process. With `clearPackageData` the orchestrator wipes that
  before the first test; without it, the leftovers are visible to the tests.
- **A container that cannot be listed runs as one test.** If its body fails outside the spec's lifecycle
  (it reads something `beforeSpec` sets up) or takes longer than 10 seconds, the container is listed as a
  whole: it runs in one process and is reported as one test, with every failure inside it in the message.
  Nothing is lost, only isolation is coarser — and it is not silent: the runner logs a warning with the
  reason under the `KotestAndroidRunner` tag while listing, and again when the container runs, where it
  lands in the test's own logcat that AGP keeps next to its result. Disabled containers are listed whole
  too, which costs nothing: they are reported as one ignored test. Duplicate test names are listed as the
  engine renames them (`(1) name`), unless `DuplicateTestNameMode.Error` makes them fail anyway.
- **Listed names must be reproducible.** A test name that changes between processes — `withData` over
  random or time-dependent values — is not found when the orchestrator asks for it, and fails with
  `… has no test named '…'`.
- **Tests must not depend on each other.** Each one runs in a fresh process, with only its parent
  containers' bodies and callbacks run before it.
- **A process per row has a cost: around half a second per test on an emulator**, on top of the test
  itself — 20 rows take some 10 seconds, 100 rows about a minute. The spec constructor, `beforeSpec` and
  the enclosing containers run again for every row. The report understates this: the time of a test, and
  so of a `testsuite` in the XML, covers only the test itself, not the process around it; the real cost
  shows in the `testsuites` total and in the duration of the Gradle task.

Only listing for the orchestrator (`listTestsForOrchestrator`) or a dry run (`-e log true`, which tools like
Marathon use to list tests) expands containers; an ordinary run does not.

## Instrumentation helpers

`io.github.dant3.kotest.android` exposes the handful of things instrumented tests always reach for:

```kotlin
instrumentation             // the Instrumentation the tests run under
targetContext               // context of the app under test
testContext                 // context of the test APK itself
instrumentationArguments    // -e key value arguments, as a Bundle

onMainThread { view.text }  // run a block on the main looper and return its result
```

Kotest test bodies run on the instrumentation thread, so anything main-thread-confined has to be
dispatched explicitly — with `onMainThread`, or through `ActivityScenario.onActivity`:

```kotlin
@RunWith(KotestAndroidRunner::class)
class ActivityTest : FunSpec({
    test("shows the greeting") {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.greeting.text.toString() shouldBe "Hello"
            }
        }
    }
})
```

`ActivityScenario` lives in `androidx.test:core`, which is not pulled in automatically — add it when you
need it.

## What comes with the dependency

| Artifact | Why |
|---|---|
| `io.kotest:kotest-framework-engine` | runs the specs |
| `io.kotest:kotest-assertions-core` | `shouldBe` and friends, version-matched to the engine |
| `junit:junit` | the `Runner` contract `AndroidJUnitRunner` speaks |
| `androidx.test:runner`, `androidx.test:monitor` | the instrumentation and its registry |

Two of Kotest's transitive artifacts are **excluded** by this module, and the exclusions are recorded in
the published POM, so nothing has to be repeated in the consuming build:

- `kotlinx-coroutines-debug` — attaches a ByteBuddy java agent; there is no `java.lang.instrument` on Android
- `classgraph` — scans a JVM classpath, which an APK does not have

## Minification

The AAR ships consumer ProGuard rules that keep Kotest's reflective entry points, every `Spec` subclass
and every `@RunWith`-annotated class, plus `-dontwarn` entries for the excluded artifacts above. A
consuming project that minifies its `androidTest` variant needs nothing extra.

## Troubleshooting

**`Could not find com.github.dant3.kotest-android-runner:runner`** — wrong coordinates; the artifact is
`com.github.dant3:kotest-android-runner`. See the note in [Installation](#installation).

**`Unable to find instrumentation info for ComponentInfo{…}`** — the test APK is not installed, or
`testInstrumentationRunner` is not set to `androidx.test.runner.AndroidJUnitRunner`.

**A selected test fails with `… has no test named '…'`** — under the orchestrator, the test got a different
name when it ran than when it was listed: names must be the same in every process, which `withData` over
random or time-dependent values is not. Typed by hand, the name must be the *flattened* path, exactly as it
appears in the report, including the ` -- ` separators and the spaces around them.

**A green run prints `INSTRUMENTATION_CODE: -1`** — that is `Activity.RESULT_OK`, the instrumentation
finishing normally; it says nothing about the tests. A failed run shows `FAILURES!!!` and the failure count.

**`Tests found` and `Tests run` differ** — in a run that does not select tests by name, only the root tests
can be announced up front (see [Current limitations](#current-limitations)); nested tests are reported as
they run. The report, not the announced count, is what to go by.

## Current limitations

Known gaps, roughly in the order they are worth closing:

- **No Kotest `AbstractProjectConfig` support.** Project config is normally discovered by scanning the
  classpath, which does not work inside an APK; a spec-level opt-in is the likely replacement.
- **No JUnit 4 `@Rule` support.** Rules (`ActivityScenarioRule`, `GrantPermissionRule`, …) are not
  applied. Use their programmatic equivalents (`ActivityScenario.launch`, `instrumentation.uiAutomation`).
- **No test-level annotation filtering.** `@LargeTest`, `@SdkSuppress`, `@RequiresDevice` and friends work
  at class level only — Kotest tests are not methods, so per-test annotations have nowhere to live.
- **Only root tests are known before a spec runs.** Whether a container holds tests or *is* one (a
  `withData` row) shows only once its body runs, so the runner announces root tests and reports nested
  ones as it discovers them. Hence the `Tests found` / `Tests run` mismatch in unfiltered runs, and sharding
  (`numShards`) that distributes root tests rather than leaves. Listing for the orchestrator is the
  exception, see [Android Test Orchestrator](#android-test-orchestrator).
- **Discovery instantiates the spec.** The spec body therefore runs once for discovery and once for
  execution; keep expensive work out of the constructor and in `beforeSpec`.

Assertions for `View`/`TextView` are deliberately **not** provided: on a device, Espresso's
`ViewMatchers` check what is actually displayed (visibility of parents, size, occlusion) and synchronise
with the UI thread, which a plain `view.visibility == VISIBLE` matcher cannot do.

## Development

```
kotest-android-runner/   the published Android library
e2e-test/                an Android library whose androidTest sources exercise the runner on a device:
                         context access, nested scopes, data tests, lifecycle callbacks, coroutines,
                         ActivityScenario — plus ReportingContractTest, which runs deliberately failing
                         probe specs through the runner and checks every JUnit event it reports
```

```bash
./gradlew :e2e-test:connectedDebugAndroidTest        # run the e2e suite on an attached device
./gradlew :e2e-test:connectedDebugAndroidTest -Pe2e.orchestrator   # the same, under the orchestrator
./gradlew detekt                                     # static analysis
./gradlew :kotest-android-runner:publishToMavenLocal # publish the AAR locally
```

A release is cut by pushing a tag; JitPack builds it on first request using `jitpack.yml`.

## Reference projects

- [LeoColman/kotest-android](https://github.com/LeoColman/kotest-android) — the closest prior art; its
  `kotest-runner-android` covers the same ground with a smaller runner (no test filtering, no
  instrumentation helpers, no consumer ProGuard rules)
- [dant3/kotest-robolectric-extension](https://github.com/dant3/kotest-robolectric-extension) — the same
  idea for JVM tests inside a Robolectric sandbox
- [kotest/kotest-extensions-robolectric](https://github.com/kotest/kotest-extensions-robolectric) —
  archived predecessor for Kotest 5.x

## License

Apache License 2.0 — see [LICENSE](LICENSE).

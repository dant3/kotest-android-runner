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
    androidTestImplementation("com.github.dant3:kotest-android-runner:0.1.0")
}
```

That single line is enough: Kotest's engine and assertions, JUnit 4 and `androidx.test:runner` come
along transitively — see [What comes with the dependency](#what-comes-with-the-dependency).

**Version catalog** (`gradle/libs.versions.toml`):

```toml
[libraries]
kotest-android-runner = { module = "com.github.dant3:kotest-android-runner", version = "0.1.0" }
```

The version slot accepts any of:

| Value | Meaning |
|---|---|
| `0.1.0` | a release tag — pinned, reproducible, recommended |
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

Extend one of the spec styles from `io.github.dant3.kotest.android` — they carry
`@RunWith(KotestAndroidRunner::class)` already:

```kotlin
import io.github.dant3.kotest.android.FunSpec
import io.github.dant3.kotest.android.targetContext
import io.kotest.matchers.shouldBe

class ContextTest : FunSpec({
    test("runs on the device, against the real framework") {
        targetContext.packageName shouldBe "com.example.app"
    }
})
```

All nine styles are available: `BehaviorSpec`, `DescribeSpec`, `ExpectSpec`, `FeatureSpec`, `FreeSpec`,
`FunSpec`, `ShouldSpec`, `StringSpec`, `WordSpec`.

Prefer the stock Kotest classes? Put the runner on the spec yourself:

```kotlin
import io.github.dant3.kotest.android.KotestAndroidRunner
import io.kotest.core.spec.style.FunSpec
import org.junit.runner.RunWith

@RunWith(KotestAndroidRunner::class)
class ContextTest : FunSpec({ /* ... */ })
```

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

- **Only leaves are reported as tests.** Containers are structure, not results.
- **A container that fails before producing any leaf** (an exception in the container body) is surfaced
  as a failure under the container's own name, so it cannot silently vanish from the report.
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

Nested tests only come into existence once a spec runs, and JUnit discards a runner whose description
has no matching child *before* it ever calls `filter`. The runner therefore reads the instrumentation's
`class` argument and announces the selected names up front, then reconciles them at run time. Containers
always execute (they have to, to reach their leaves); leaves that were not selected are skipped and left
out of the report.

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

**A filtered run reports `OK (0 tests)`** — the test name must be the *flattened* path, exactly as it
appears in the report, including the ` -- ` separators and the spaces around them.

## Current limitations

Known gaps, roughly in the order they are worth closing:

- **No Kotest `AbstractProjectConfig` support.** Project config is normally discovered by scanning the
  classpath, which does not work inside an APK; a spec-level opt-in is the likely replacement.
- **No JUnit 4 `@Rule` support.** Rules (`ActivityScenarioRule`, `GrantPermissionRule`, …) are not
  applied. Use their programmatic equivalents (`ActivityScenario.launch`, `instrumentation.uiAutomation`).
- **No test-level annotation filtering.** `@LargeTest`, `@SdkSuppress`, `@RequiresDevice` and friends work
  at class level only — Kotest tests are not methods, so per-test annotations have nowhere to live.
- **`getDescription()` instantiates the spec.** The spec body therefore runs once for discovery and once
  for execution; keep expensive work out of the constructor and in `beforeSpec`.
- **Orchestrator and sharding are untested.** Both go through the same `class` argument and should work,
  but there is no coverage for them yet.

Assertions for `View`/`TextView` are deliberately **not** provided: on a device, Espresso's
`ViewMatchers` check what is actually displayed (visibility of parents, size, occlusion) and synchronise
with the UI thread, which a plain `view.visibility == VISIBLE` matcher cannot do.

## Development

```
kotest-android-runner/   the published Android library
e2e-test/                an Android library whose androidTest sources exercise the runner on a device:
                         context access, nested scopes, lifecycle callbacks, coroutines, ActivityScenario
```

```bash
./gradlew :e2e-test:connectedDebugAndroidTest        # run the e2e suite on an attached device
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

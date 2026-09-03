import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    `maven-publish`
}

android {
    namespace = "io.github.dant3.kotest.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    // Consumers on JDK 17 must be able to compile against the AAR, so the bytecode targets 17
    // even though the library itself is built with a 21 toolchain.
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Kotest's JVM engine drags in artifacts that cannot be dexed or are useless on a device:
    //  - kotlinx-coroutines-debug attaches a ByteBuddy java agent (no java.lang.instrument on Android)
    //  - classgraph scans a JVM classpath, which does not exist inside an APK
    api(libs.kotest.framework.engine) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-debug")
        exclude(group = "io.github.classgraph", module = "classgraph")
    }
    api(libs.kotest.assertions.core)
    api(libs.junit4)
    // The stock AndroidX runner is what discovers and drives the specs, and the registry is how
    // tests reach the instrumentation — a consumer needs both, so they come along with this module.
    api(libs.androidx.test.runner)
    api(libs.androidx.test.monitor)

    implementation(libs.kotlin.reflect)

    detektPlugins(libs.gradlePlugin.detekt.formatting)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootDir.resolve("gradle/detekt.yml"))
    source.setFrom(files("src/main/kotlin"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            artifactId = "kotest-android-runner"
            pom {
                name.set("kotest-android-runner")
                description.set(
                    "JUnit 4 runner that executes Kotest specs as Android instrumented tests on a device or emulator.",
                )
                url.set("https://github.com/dant3/kotest-android-runner")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}

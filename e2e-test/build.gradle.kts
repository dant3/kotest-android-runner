plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
}

android {
    namespace = "io.github.dant3.kotest.android.e2e"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "win32-x86/**",
                "win32-x86-64/**",
            )
        }
    }

    // Pre-declare singleVariant so JitPack does not auto-inject its Groovy-syntax variant
    // into this Kotlin DSL file. This module is not published; the block is here purely to
    // keep JitPack's file modifier from corrupting the script.
    publishing {
        singleVariant("release") {}
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // kotest-assertions-core, junit4 and androidx.test:runner come transitively with the runner.
    androidTestImplementation(project(":kotest-android-runner"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)

    detektPlugins(libs.gradlePlugin.detekt.formatting)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootDir.resolve("gradle/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/androidTest/kotlin"))
}

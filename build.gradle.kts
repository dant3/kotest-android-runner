plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    group = "io.github.dant3.kotest.android"
    version = "1.0.0"
}

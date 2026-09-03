# Kotest instantiates spec classes reflectively and reads Kotlin metadata for display names.
-keep class io.kotest.** { *; }
-keep interface io.kotest.** { *; }
-dontwarn io.kotest.**

# Spec classes are only referenced through the JUnit 4 runner, never called directly.
-keep @org.junit.runner.RunWith class * { *; }
-keep class * extends io.kotest.core.spec.Spec { *; }

# Optional Kotest dependencies that are absent (or unusable) in an APK.
-dontwarn io.github.classgraph.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn net.bytebuddy.**
-dontwarn java.lang.instrument.**

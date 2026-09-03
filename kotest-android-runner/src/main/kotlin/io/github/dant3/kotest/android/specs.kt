package io.github.dant3.kotest.android

import org.junit.runner.RunWith

/**
 * Kotest spec styles pre-wired with [KotestAndroidRunner]. Extend these from `androidTest`
 * instead of the `io.kotest.core.spec.style` originals — or keep the originals and put
 * `@RunWith(KotestAndroidRunner::class)` on the class yourself.
 */

@RunWith(KotestAndroidRunner::class)
public abstract class BehaviorSpec(
    body: io.kotest.core.spec.style.BehaviorSpec.() -> Unit = {},
) : io.kotest.core.spec.style.BehaviorSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class DescribeSpec(
    body: io.kotest.core.spec.style.DescribeSpec.() -> Unit = {},
) : io.kotest.core.spec.style.DescribeSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class ExpectSpec(
    body: io.kotest.core.spec.style.ExpectSpec.() -> Unit = {},
) : io.kotest.core.spec.style.ExpectSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class FeatureSpec(
    body: io.kotest.core.spec.style.FeatureSpec.() -> Unit = {},
) : io.kotest.core.spec.style.FeatureSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class FreeSpec(
    body: io.kotest.core.spec.style.FreeSpec.() -> Unit = {},
) : io.kotest.core.spec.style.FreeSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class FunSpec(
    body: io.kotest.core.spec.style.FunSpec.() -> Unit = {},
) : io.kotest.core.spec.style.FunSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class ShouldSpec(
    body: io.kotest.core.spec.style.ShouldSpec.() -> Unit = {},
) : io.kotest.core.spec.style.ShouldSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class StringSpec(
    body: io.kotest.core.spec.style.StringSpec.() -> Unit = {},
) : io.kotest.core.spec.style.StringSpec(body)

@RunWith(KotestAndroidRunner::class)
public abstract class WordSpec(
    body: io.kotest.core.spec.style.WordSpec.() -> Unit = {},
) : io.kotest.core.spec.style.WordSpec(body)

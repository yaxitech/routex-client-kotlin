package tech.yaxi.routex.settlement

/**
 * Marks shared online tests (live YAXI API). Inert on Kotlin/Native; gating
 * happens via the Gradle `*OnlineTest` name filters.
 */
@Target(AnnotationTarget.CLASS)
annotation class OnlineTest

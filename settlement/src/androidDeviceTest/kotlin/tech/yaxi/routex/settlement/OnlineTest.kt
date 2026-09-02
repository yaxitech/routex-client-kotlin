package tech.yaxi.routex.settlement

/**
 * Marks shared online tests (live YAXI API). Inert on Android: the device
 * test task runs them unconditionally and requires credentials up front.
 */
@Target(AnnotationTarget.CLASS)
annotation class OnlineTest

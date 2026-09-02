package tech.yaxi.routex.client

import org.junit.jupiter.api.Tag

/**
 * Marks shared online tests (live YAXI API). Carries the JUnit 5 `online` tag
 * so the default `jvmTest` run excludes them; `jvmOnlineTest` includes them.
 * AndroidJUnitRunner ignores the tag.
 */
@Target(AnnotationTarget.CLASS)
@Tag("online")
annotation class OnlineTest

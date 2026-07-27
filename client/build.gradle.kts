plugins {
    id("routex.kmp-library")
    id("routex.lint")
    id("routex.coverage")
    id("routex.publishing")
    kotlin("plugin.serialization")
}

// Online-test sources are stitched into the canonical `jvmTest` compilation via
// `kotlin.srcDir(...)`; on Android they go into `androidDeviceTest` the same way.
// Online test classes carry `@EnabledIfSystemProperty(named = "routex.online", matches = "true")`,
// so the standard `:check` `jvmTest` task skips them at runtime, the dedicated
// `:jvmOnlineTest` task sets the property to run them, and IntelliJ's per-test
// gutter (which goes through `:jvmTest --tests "..."`) auto-enables the property
// when an explicit test filter is present so the requested test executes.
// Keeping everything in the canonical `jvmTest` compilation lets IntelliJ resolve
// the runtime classpath; a custom `compilations.create("onlineTest")` does NOT
// (see YouTrack KT-46878 for the IDE limitation).
val commonOnlineTestSrc: String = "src/commonOnlineTest/kotlin"
val jvmAndAndroidOnlineTestSrc: String = "src/jvmAndAndroidOnlineTest/kotlin"

val (generateTestCredentials, testCredentialsDir) = registerGenerateTestCredentials()

kotlin {
    compilerOptions {
        optIn.add("tech.yaxi.routex.models.InternalRoutexApi")
    }

    android {
        namespace = "tech.yaxi.routex.client"
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm()

    js(IR) {
        nodejs()
        useEsModules()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":models"))
                api(project(":transport"))
                api(project(":settlement"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.jdk8)
            }
        }
        commonTest {
            // Compiles `@sample` snippets so they stay in sync with the public API;
            // Dokka picks up the same directory via `samples.from(...)` below.
            kotlin.srcDir("src/samples/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            kotlin.srcDir("src/jvmOracleTest/kotlin")
            kotlin.srcDir(commonOnlineTestSrc)
            kotlin.srcDir(jvmAndAndroidOnlineTestSrc)
            kotlin.srcDir(generateTestCredentials.map { testCredentialsDir.get().asFile })
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.junit5)
                implementation(project.dependencies.platform(libs.junit.bom))
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
                runtimeOnly(libs.slf4j.simple)
                implementation(project(":tickets"))
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
                implementation(libs.bouncycastle.prov)
            }
        }
        getByName("androidHostTest") {
            // Offline host-side unit tests for the Android target. Runs on the JVM
            // (no device or emulator required) so `:check` always has an Android
            // signal that doesn't depend on `connectedAndroidDeviceTest`.
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.junit5)
                implementation(project.dependencies.platform(libs.junit.bom))
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir(commonOnlineTestSrc)
            kotlin.srcDir(jvmAndAndroidOnlineTestSrc)
            kotlin.srcDir(generateTestCredentials.map { testCredentialsDir.get().asFile })
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                // Shared online tests carry `@Tag("online")` (JUnit 5 metadata) for
                // the JVM-side filter on `:check`. AndroidJUnitRunner ignores the
                // annotation at runtime; pull the api-only artifact in compileOnly so
                // it never reaches dexing (JUnit 6 jars use bytecode D8 can't lower).
                compileOnly(project.dependencies.platform(libs.junit.bom))
                compileOnly(libs.junit.jupiter.api)
                implementation(project(":tickets"))
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
                implementation(libs.bouncycastle.prov)
            }
        }
    }
}

tasks.named<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>("jsNodeTest") {
    // commonTest includes only @sample snippets (no @Test methods), so the JS runner
    // will never discover any tests.
    failOnNoDiscoveredTests = false
}

tasks.named<Test>("jvmTest") {
    // Default `:check` skips `@Tag("online")` tests. An explicit `--tests` filter
    // (typical for IntelliJ's per-test gutter run) lifts the exclusion so the
    // requested test executes even when online-tagged.
    useJUnitPlatform { excludeTags("online") }
    doFirst {
        @Suppress("UnstableApiUsage")
        val cli = (filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter).commandLineIncludePatterns
        val explicit = cli.isNotEmpty() || filter.includePatterns.isNotEmpty()
        if (explicit) {
            (options as org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions).excludeTags = LinkedHashSet()
        }
    }
}

val jvmOnlineTest = tasks.register<Test>("jvmOnlineTest") {
    description = "Runs online integration tests against the live YAXI API (opt-in)."
    group = "verification"
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    useJUnitPlatform { includeTags("online") }
    // Live YAXI rate limits break under concurrent load; keep online tests sequential.
    maxParallelForks = 1
    requireYaxiOnlineCredentials()
}

tasks.matching { it.name == "connectedAndroidDeviceTest" }.configureEach {
    requireYaxiOnlineCredentials()
}

listOf("generateAndroidDeviceTestLintModel", "lintAnalyzeAndroidDeviceTest")
    .forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach { dependsOn(generateTestCredentials) }
    }

dokka {
    dokkaSourceSets.named("commonMain") {
        samples.from("src/samples/kotlin")
        includes.from("Module.md")
    }
}

mavenPublishing {
    coordinates(group.toString(), "routex-client", version.toString())
    pom {
        name.set("routex-client")
        description.set("Kotlin Multiplatform client for YAXI's Open Banking services.")
    }
}

plugins {
    id("routex.kmp-jvm-library")
    id("routex.lint")
    id("routex.coverage")
    id("routex.publishing")
    kotlin("plugin.serialization")
}

// Refresh-client is intentionally JVM-only: the non-interactive flow it exposes runs on
// backend services that use a stored ConnectionData to refresh balances/transactions
// without user interaction. Android consumers should use :client (which carries the
// interactive consent flow) instead.

val (generateTestCredentials, testCredentialsDir) = registerGenerateTestCredentials()

kotlin {
    compilerOptions {
        optIn.add("tech.yaxi.routex.models.InternalRoutexApi")
    }

    jvm()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":models"))
                api(project(":transport"))
                api(project(":settlement"))
                api(project(":tickets"))
                api(libs.kotlinx.coroutines.core)
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
            kotlin.srcDir("src/commonOnlineTest/kotlin")
            kotlin.srcDir(generateTestCredentials.map { testCredentialsDir.get().asFile })
            dependencies {
                implementation(project(":client"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.junit5)
                implementation(project.dependencies.platform(libs.junit.bom))
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
                runtimeOnly(libs.slf4j.simple)
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
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

dokka {
    dokkaSourceSets.named("commonMain") {
        samples.from("src/samples/kotlin")
        includes.from("Module.md")
    }
}

mavenPublishing {
    coordinates(group.toString(), "routex-refresh-client", version.toString())
    pom {
        name.set("routex-refresh-client")
        description.set(
            "Non-interactive refresh client for YAXI Routex (Kotlin, JVM only). " +
                "For Android consumers, use routex-client.",
        )
    }
}

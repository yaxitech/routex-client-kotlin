plugins {
    id("routex.kmp-library")
    id("routex.lint")
    id("routex.coverage")
    id("routex.publishing")
    kotlin("plugin.serialization")
}

kotlin {
    compilerOptions {
        // Tests in this module exercise the @InternalRoutexApi declarations they own.
        optIn.add("tech.yaxi.routex.models.InternalRoutexApi")
    }

    android {
        namespace = "tech.yaxi.routex.models"
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
                api(libs.ionspin.bignum)
                implementation(libs.ionspin.bignum.serialization.kotlinx)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        jvmTest {
            // Oracle-fixture conformance tests live alongside JVM unit tests so the
            // standard `jvmTest` Test task (which Kover instruments out-of-the-box)
            // covers them too. Kept in their own `src/jvmOracleTest/kotlin` directory
            // for code-organization only.
            kotlin.srcDir("src/jvmOracleTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.junit5)
                implementation(project.dependencies.platform(libs.junit.bom))
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

dokka {
    dokkaSourceSets.named("commonMain") {
        includes.from("Module.md")
    }
}

mavenPublishing {
    coordinates(group.toString(), "routex-models", version.toString())
    pom {
        name.set("routex-models")
        description.set(
            "Internal: wire DTOs shared by routex-client, routex-refresh-client, and " +
                "routex-settlement. Not intended for direct consumption; depend on it transitively " +
                "via the client artifacts. May break between minor releases.",
        )
    }
}

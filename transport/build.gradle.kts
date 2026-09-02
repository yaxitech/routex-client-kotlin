plugins {
    id("routex.kmp-library")
    id("routex.lint")
    id("routex.coverage")
    id("routex.publishing")
}

kotlin {
    android {
        namespace = "tech.yaxi.routex.transport"
    }

    js {
        nodejs()
        useEsModules()
    }

    sourceSets {
        // JVM and Android share a single CIO-backed `defaultHttpTransport()` actual.
        val jvmAndAndroidMain =
            create("jvmAndAndroidMain") {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(libs.ktor.client.cio)
                }
            }
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        androidMain.get().dependsOn(jvmAndAndroidMain)

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                // Ktor's `Url` type is exposed on `HttpRequest.url` so consumers don't
                // have to round-trip through string concatenation; the engine modules
                // (CIO et al.) stay on `implementation(...)` and remain swappable.
                api(libs.ktor.client.core)
            }
        }
        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
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
    coordinates(group.toString(), "routex-transport", version.toString())
    pom {
        name.set("routex-transport")
        description.set(
            "Internal: HTTP transport abstraction shared by routex-client, " +
                "routex-refresh-client, and routex-settlement. Not intended for direct " +
                "consumption; depend on it transitively via the client artifacts. May break " +
                "between minor releases.",
        )
    }
}

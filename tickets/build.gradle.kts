plugins {
    id("routex.kmp-jvm-library")
    id("routex.lint")
    id("routex.coverage")
    id("routex.publishing")
    kotlin("plugin.serialization")
}

kotlin {
    compilerOptions {
        optIn.add("tech.yaxi.routex.models.InternalRoutexApi")
    }

    jvm()

    js {
        nodejs()
        useEsModules()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":models"))
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        jsMain {
            dependencies {
                implementation(npm("@noble/hashes", "^2.0.0"))
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        jvmTest {
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

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

dokka {
    dokkaSourceSets.named("commonMain") {
        includes.from("Module.md")
    }
}

mavenPublishing {
    coordinates(group.toString(), "routex-tickets", version.toString())
    pom {
        name.set("routex-tickets")
        description.set("Typed ticket issuer for the YAXI routex services.")
    }
}

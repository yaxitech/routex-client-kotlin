import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // AGP's KMP-library plugin doesn't register a top-level `lint` aggregator
    // on its own; `com.android.lint` adds `lint` / `lintFix` / `updateLintBaseline`.
    id("com.android.lint")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

kotlin {
    explicitApi = ExplicitApiMode.Strict

    // Java toolchain version pinned for compilation; Gradle's Foojay resolver
    // provisions it on demand when not present locally.
    jvmToolchain(25)

    compilerOptions {
        progressiveMode = true
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("kotlin.io.encoding.ExperimentalEncodingApi")
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xreport-all-warnings",
            "-Xrender-internal-diagnostic-names",
        )
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            // Reject standard-library API introduced after JVM 17.
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }

    android {
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }
}

// `allWarningsAsErrors` only on production sources; test code legitimately
// exercises deprecated API on the way to verifying it.
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    val isMainCompilation = name.startsWith("compileKotlin")
    compilerOptions { allWarningsAsErrors = isMainCompilation }
}

lint {
    abortOnError = true
    warningsAsErrors = true
    checkDependencies = false
    checkReleaseBuilds = true
    disable +=
        setOf(
            // Network-dependent informational checks; pinning JDK + SDK in
            // the dev shell is more authoritative than chasing every release.
            "GradleDependency",
            "NewerVersionAvailable",
            // SecureRandom advisory targets API < 18; minSdk = 24 excludes it.
            "TrulyRandom",
        )
    val baselineFile = layout.projectDirectory.file("lint-baseline.xml").asFile
    if (baselineFile.exists()) {
        baseline = baselineFile
    }
}

tasks.named("check") { dependsOn(tasks.named("lint")) }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

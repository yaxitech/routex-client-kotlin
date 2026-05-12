import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    explicitApi = ExplicitApiMode.Strict

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
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    val isMainCompilation = name.startsWith("compileKotlin")
    compilerOptions { allWarningsAsErrors = isMainCompilation }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

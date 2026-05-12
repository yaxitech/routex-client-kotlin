plugins {
    `kotlin-dsl`
}

// Gradle 9.0.0 embeds Kotlin 2.2, which doesn't target JVM 25 yet. Pin
// buildSrc to JVM 17 to keep `compileJava` and `compileKotlin` in sync.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.build.kotlin.gradle)
    implementation(libs.build.kotlin.serialization.gradle)
    implementation(libs.build.android.gradle)
    implementation(libs.build.ktlint.gradle)
    implementation(libs.build.detekt.gradle)
    implementation(libs.build.kover.gradle)
    implementation(libs.build.dokka.gradle)
    implementation(libs.build.mavenPublish.gradle)
    // Exposes the `libs` version-catalog accessor to precompiled script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

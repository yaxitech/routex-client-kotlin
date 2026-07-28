plugins {
    `kotlin-dsl`
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

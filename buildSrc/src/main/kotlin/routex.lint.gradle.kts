plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

ktlint {
    verbose.set(true)
    android.set(false)
    filter {
        exclude { it.file.path.contains("build/") }
    }
}

detekt {
    buildUponDefaultConfig.set(true)
    allRules.set(false)
    parallel.set(true)
    basePath.set(rootDir)
    config.setFrom(rootProject.layout.projectDirectory.file("detekt.yml"))
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("17")
    reports {
        html.required.set(true)
        sarif.required.set(false)
        markdown.required.set(false)
        checkstyle.required.set(true)
    }
}

tasks.named("check") {
    dependsOn(tasks.named("detekt"))
}

tasks.register("format") {
    description = "Apply every auto-fix the code-quality tools support (ktlint, detekt autoCorrect)."
    group = "formatting"
    dependsOn(tasks.named("ktlintFormat"))
}

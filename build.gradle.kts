// Plugin versions are declared in gradle/libs.versions.toml and brought
// onto the classpath by buildSrc. Subprojects apply them via the
// `routex.*` convention plugins in buildSrc/.

plugins {
    id("org.jetbrains.dokka")
    base
}

// Aggregate Dokka HTML across all published modules so KDoc references can
// cross module boundaries. Per-module `dokkaGenerateHtml` (used by each
// module's javadocJar) still runs independently.
dependencies {
    dokka(project(":models"))
    dokka(project(":transport"))
    dokka(project(":settlement"))
    dokka(project(":client"))
    dokka(project(":refresh-client"))
    dokka(project(":tickets"))
}

// `refresh-client-demo/` is a standalone Gradle build kept here for proximity to the
// source it demos. The wrappers below run it against the in-repo modules in CI; the
// `tech.yaxi.routex.demo.use_local` toggle gates the substitution.
val demoDir = layout.projectDirectory.dir("refresh-client-demo")
val demoWrapper = demoDir.file("gradlew").asFile.absolutePath
val demoBuildFiles =
    listOf(
        demoDir.file("kotlin-demo/build.gradle.kts").asFile,
        demoDir.file("java-demo/build.gradle.kts").asFile,
    )

val routexVersion: Provider<String> = routexClientVersion()

val demoAssemble by tasks.registering(Exec::class) {
    description = "Compiles refresh-client-demo against the in-repo :client / :refresh-client."
    group = "verification"
    workingDir = demoDir.asFile
    executable = demoWrapper
    args(":kotlin-demo:assemble", ":java-demo:assemble", "-Ptech.yaxi.routex.demo.use_local=true")
}

val demoOnlineTest by tasks.registering(Exec::class) {
    description = "Runs the refresh-client-demo smoke tests against the live YAXI API."
    group = "verification"
    workingDir = demoDir.asFile
    executable = demoWrapper
    args(":kotlin-demo:test", ":java-demo:test", "-Ptech.yaxi.routex.demo.use_local=true")

    val urlProvider = providers.provider { yaxiTestConfigFromLocalProperties(YaxiTestConfigKeys.URL) }
    val keyIdProvider = providers.provider { yaxiTestConfigFromLocalProperties(YaxiTestConfigKeys.API_KEY_ID) }
    val keyProvider = providers.provider { yaxiTestConfigFromLocalProperties(YaxiTestConfigKeys.API_KEY) }
    inputs.property("apiUrl", urlProvider)
    inputs.property("apiKeyId", keyIdProvider)
    inputs.property("apiKey", keyProvider)

    requireYaxiOnlineCredentials()
    doFirst {
        environment("YAXI_API_URL", urlProvider.get())
        environment("YAXI_API_KEY_ID", keyIdProvider.get())
        environment("YAXI_API_KEY_SECRET", keyProvider.get())
    }
}

val checkDemoVersionPin by tasks.registering {
    description = "Asserts refresh-client-demo's hardcoded routex coordinate version matches the parent's."
    group = "verification"
    // Bind script-scope state into task-local vals so the doLast closure stays
    // serializable for the configuration cache. Pin against the canonical base
    // (not the SNAPSHOT-suffixed publish version) because the demo always ships
    // against the latest released Maven Central coordinate.
    val expectedVersion = providers.gradleProperty("tech.yaxi.routex.version.base")
    val files = demoBuildFiles
    val rootDirPath = rootDir
    inputs.property("parentBaseVersion", expectedVersion)
    files.forEach { inputs.file(it) }
    doLast {
        val expected = expectedVersion.get()
        val coordsRegex = Regex("""tech\.yaxi:routex(?:-refresh-client|-client|-tickets):([^"]+)""")
        val mismatches =
            files.flatMap { file ->
                coordsRegex.findAll(file.readText())
                    .map { it.groupValues[1] }
                    .filter { it != expected }
                    .map { actual -> "${file.relativeTo(rootDirPath)}: $actual (expected $expected)" }
                    .toList()
            }
        check(mismatches.isEmpty()) {
            "refresh-client-demo version pin out of sync with tech.yaxi.routex.version.base:\n" +
                mismatches.joinToString("\n") { "  $it" }
        }
    }
}

val demoZip by tasks.registering(Zip::class) {
    description = "Produces a self-contained zip of refresh-client-demo."
    group = "distribution"
    archiveBaseName.set("refresh-client-demo")
    archiveVersion.set(routexVersion)
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    into(provider { "${archiveBaseName.get()}-${archiveVersion.get()}" })
    from(demoDir) {
        exclude(".env")
        exclude("local.properties")
        exclude("DEVELOPING.md")
        exclude("**/.gradle", "**/.gradle/**")
        exclude("**/build", "**/build/**")
        exclude("**/.idea", "**/.idea/**")
        exclude("**/.kotlin", "**/.kotlin/**")
        exclude("**/*.iml")
        // Preserve gradlew's executable bit; zip metadata default would otherwise
        // strip it on extract.
        filesMatching("gradlew") { permissions { unix("0755") } }
    }
    dependsOn(checkDemoVersionPin)
}

val checkDemoWrapperPin by tasks.registering {
    description = "Asserts refresh-client-demo's Gradle wrapper distribution matches the parent's."
    group = "verification"
    val parentWrapper = layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties").asFile
    val demoWrapper = demoDir.file("gradle/wrapper/gradle-wrapper.properties").asFile
    val rootDirPath = rootDir
    inputs.file(parentWrapper)
    inputs.file(demoWrapper)
    doLast {
        val urlRegex = Regex("""(?m)^distributionUrl=(.+)$""")
        fun urlFrom(file: java.io.File): String =
            urlRegex.find(file.readText())?.groupValues?.get(1)
                ?: error("distributionUrl not found in ${file.relativeTo(rootDirPath)}")
        val parentUrl = urlFrom(parentWrapper)
        val demoUrl = urlFrom(demoWrapper)
        check(parentUrl == demoUrl) {
            "Gradle wrapper distributionUrl mismatch (causes K2 daemon races under parallel " +
                "execution; bump both to the same version via `./gradlew wrapper " +
                "--gradle-version <x.y.z>` in each):\n" +
                "  ${parentWrapper.relativeTo(rootDirPath)}: $parentUrl\n" +
                "  ${demoWrapper.relativeTo(rootDirPath)}: $demoUrl"
        }
    }
}

tasks.named("check") {
    dependsOn(demoAssemble, checkDemoVersionPin, checkDemoWrapperPin)
}

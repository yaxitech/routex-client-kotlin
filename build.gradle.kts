// Plugin versions are declared in gradle/libs.versions.toml and brought
// onto the classpath by buildSrc. Subprojects apply them via the
// `routex.*` convention plugins in buildSrc/.

plugins {
    id("org.jetbrains.dokka")
    base
}

allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download = false
    }
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

val routexVersion: Provider<String> = routexClientVersion()

val refreshClientDemoAssemble = tasks.register<Exec>("refreshClientDemoAssemble") {
    description = "Compiles refresh-client-demo against the in-repo :client / :refresh-client."
    group = "verification"
    workingDir = demoDir.asFile
    executable = demoWrapper
    args(":kotlin-demo:assemble", ":java-demo:assemble", "-Ptech.yaxi.routex.demo.use_local=true")
}

tasks.register<Exec>("demoOnlineTest") {
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

// Pin against the canonical base, not the SNAPSHOT-suffixed publish version:
// the demos always ship against the latest released coordinate.
val releasedCoordinateFiles =
    listOf(
        layout.projectDirectory.file("README.md"),
        demoDir.file("kotlin-demo/build.gradle.kts"),
        demoDir.file("java-demo/build.gradle.kts"),
    ).map { it.asFile }

val checkReleasedCoordinatePins = tasks.register("checkReleasedCoordinatePins") {
    description = "Asserts every released routex coordinate matches tech.yaxi.routex.version.base."
    group = "verification"
    // Bind script-scope state into task-local vals so the doLast closure stays
    // serializable for the configuration cache.
    val expectedVersion = providers.gradleProperty("tech.yaxi.routex.version.base")
    val files = releasedCoordinateFiles
    val rootDirPath = rootDir
    inputs.property("parentBaseVersion", expectedVersion)
    files.forEach { inputs.file(it) }
    doLast {
        val expected = expectedVersion.get()
        val coordsRegex = Regex("""tech\.yaxi:routex-[a-z-]+:([^"]+)""")
        val problems =
            files.flatMap { file ->
                val name = file.relativeTo(rootDirPath)
                val found = coordsRegex.findAll(file.readText()).map { it.groupValues[1] }.toList()
                if (found.isEmpty()) {
                    listOf("$name: no routex coordinate found")
                } else {
                    found.filter { it != expected }.map { "$name: $it (expected $expected)" }
                }
            }
        check(problems.isEmpty()) {
            "released routex coordinate out of sync with tech.yaxi.routex.version.base:\n" +
                problems.joinToString("\n") { "  $it" }
        }
    }
}

tasks.register<Zip>("demoZip") {
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
    dependsOn(checkReleasedCoordinatePins)
}

val checkRefreshClientDemoWrapperPin = tasks.register("checkRefreshClientDemoWrapperPin") {
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
    dependsOn(refreshClientDemoAssemble, checkReleasedCoordinatePins, checkRefreshClientDemoWrapperPin)
}

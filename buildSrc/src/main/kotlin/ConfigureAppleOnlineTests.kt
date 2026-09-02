import org.gradle.api.Project
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

/**
 * Gates `macosArm64OnlineTest` behind `-Proutex.online=true` and lets an
 * explicit `--tests` filter run online tests through `macosArm64Test`
 * (IntelliJ's per-test gutter), mirroring `jvmTest`/`jvmOnlineTest`.
 */
fun Project.configureAppleOnlineTests() {
    val online = providers.gradleProperty("routex.online").map { it == "true" }.getOrElse(false)

    tasks.withType(KotlinTest::class.java).configureEach {
        when (name) {
            "macosArm64OnlineTest" -> {
                description = "Runs online integration tests against the live YAXI API (opt-in)."
                group = "verification"
                onlyIf("online tests are opt-in; pass -Proutex.online=true") { online }
                requireYaxiOnlineCredentials()
            }
            "macosArm64Test" ->
                doFirst {
                    // `includePatterns` folds in `commandLineIncludePatterns` and both
                    // are read at task-action time, so this reaches the binary's argv.
                    @Suppress("UnstableApiUsage")
                    val cli = (filter as DefaultTestFilter).commandLineIncludePatterns
                    if (cli.isNotEmpty() || filter.includePatterns.isNotEmpty()) {
                        filter.setExcludePatterns()
                    }
                }
        }
    }
}

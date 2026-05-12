import org.gradle.api.Task

/**
 * Fail before running a live YAXI integration task when required credentials
 * are missing from `local.properties`.
 */
fun Task.requireYaxiOnlineCredentials() {
    val resolved =
        YaxiTestConfigKeys.REQUIRED.associateWith { project.yaxiTestConfigFromLocalProperties(it) }
    doFirst {
        val missing = resolved.filterValues { it.isEmpty() }.keys
        check(missing.isEmpty()) {
            buildString {
                append("Cannot run $path: missing required credential(s) in ")
                append("local.properties:\n")
                missing.forEach { key -> append("  $key\n") }
            }
        }
    }
}

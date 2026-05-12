import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Effective publish version for the routex-client artifacts.
 *
 * Reads `tech.yaxi.routex.version` if set explicitly (release builds); otherwise
 * appends `-SNAPSHOT` to `tech.yaxi.routex.version.base` from `gradle.properties`.
 * That base property is the single canonical place to bump the version.
 */
fun Project.routexClientVersion(): Provider<String> {
    val explicit = providers.gradleProperty("tech.yaxi.routex.version")
    val base = providers.gradleProperty("tech.yaxi.routex.version.base")
    return explicit.orElse(base.map { "$it-SNAPSHOT" })
}

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.util.Properties

/** `local.properties` keys that configure the YAXI test environment. */
object YaxiTestConfigKeys {
    const val URL: String = "tech.yaxi.routex.url"
    const val API_KEY_ID: String = "tech.yaxi.routex.api_key_id"
    const val API_KEY: String = "tech.yaxi.routex.api_key"

    /** Set when running online tests against a routex backend that signs with test keys. */
    const val TEST_SIGNING_KEYS: String = "tech.yaxi.routex.test_signing_keys"

    /** Keys required for online tests; reported when missing. */
    val REQUIRED: List<String> = listOf(URL, API_KEY_ID, API_KEY)
}

/** Read a value from `local.properties`; empty when absent or unset. */
fun Project.yaxiTestConfigFromLocalProperties(propertyKey: String): String {
    val file = rootProject.file("local.properties")
    if (!file.exists()) {
        return ""
    }
    val props = Properties()
    file.inputStream().use { props.load(it) }
    return props.getProperty(propertyKey).orEmpty()
}

/**
 * Register a `generateTestCredentials` task that emits `TestCredentials.kt` from
 * `local.properties`. Hooked into `prepareKotlinIdeaImport` so the file
 * materialises during IDE sync.
 */
fun Project.registerGenerateTestCredentials(): Pair<TaskProvider<*>, Provider<Directory>> {
    val outputDir = layout.buildDirectory.dir("generated/testCredentials/kotlin")
    val urlProvider = providers.provider { yaxiTestConfigFromLocalProperties(YaxiTestConfigKeys.URL) }
    val keyIdProvider = providers.provider { yaxiTestConfigFromLocalProperties(YaxiTestConfigKeys.API_KEY_ID) }
    val keyProvider = providers.provider { yaxiTestConfigFromLocalProperties(YaxiTestConfigKeys.API_KEY) }
    val testSigningKeysProvider =
        providers.provider {
            yaxiTestConfigFromLocalProperties(YaxiTestConfigKeys.TEST_SIGNING_KEYS)
                .equals("true", ignoreCase = true)
        }
    val task =
        tasks.register("generateTestCredentials") {
            inputs.property("url", urlProvider)
            inputs.property("apiKeyId", keyIdProvider)
            inputs.property("apiKey", keyProvider)
            inputs.property("testSigningKeys", testSigningKeysProvider)
            outputs.dir(outputDir)
            doLast {
                val packageDir = outputDir.get().asFile.resolve("tech/yaxi/routex/testing/internal")
                packageDir.mkdirs()
                packageDir.resolve("TestCredentials.kt").writeText(
                    """
                    |// Generated from local.properties, do not edit.
                    |package tech.yaxi.routex.testing.internal
                    |
                    |internal object TestCredentials {
                    |    const val URL: String = "${urlProvider.get()}"
                    |    const val API_KEY_ID: String = "${keyIdProvider.get()}"
                    |    const val API_KEY: String = "${keyProvider.get()}"
                    |    const val TEST_SIGNING_KEYS: Boolean = ${testSigningKeysProvider.get()}
                    |}
                    """.trimMargin() + "\n",
                )
            }
        }
    tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach { dependsOn(task) }
    return Pair(task, outputDir)
}

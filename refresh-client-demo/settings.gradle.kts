rootProject.name = "refresh-client-demo"

include(":java-demo", ":kotlin-demo")

// In local-build mode (`tech.yaxi.routex.demo.use_local=true`), substitute the
// in-repo `:client` / `:refresh-client` modules for the Maven Central coordinates.
// Read from CLI -P, env var, or local.properties (manually, since Gradle does not
// auto-load local.properties in settings scripts).
val useLocalKey = "tech.yaxi.routex.demo.use_local"

fun resolveUseLocal(): Boolean {
    settings.startParameter.projectProperties[useLocalKey]?.let { return it.toBoolean() }
    System.getenv(useLocalKey.replace('.', '_').uppercase())?.let { return it.toBoolean() }
    listOf("local.properties", "../local.properties")
        .map { settings.rootDir.resolve(it) }
        .firstOrNull { it.isFile }
        ?.let { file ->
            val props = java.util.Properties().also { p -> file.inputStream().use { p.load(it) } }
            props.getProperty(useLocalKey)?.let { return it.toBoolean() }
        }
    return false
}

if (resolveUseLocal()) {
    // The KMP modules' Gradle project names (`:client`, `:refresh-client`, ...) don't
    // match the published Maven coordinates (`tech.yaxi:routex-client`, ...), so
    // includeBuild's auto-substitution misses. Wire the mapping explicitly.
    includeBuild("..") {
        dependencySubstitution {
            substitute(module("tech.yaxi:routex-client")).using(project(":client"))
            substitute(module("tech.yaxi:routex-refresh-client")).using(project(":refresh-client"))
            substitute(module("tech.yaxi:routex-models")).using(project(":models"))
            substitute(module("tech.yaxi:routex-settlement")).using(project(":settlement"))
            substitute(module("tech.yaxi:routex-transport")).using(project(":transport"))
        }
    }
}

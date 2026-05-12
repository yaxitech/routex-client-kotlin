import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.dokka.gradle.engine.parameters.KotlinPlatform

plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
    // The `kotlin {}` extension used in the Dokka source-set workaround below is
    // contributed by `routex.kmp-library` / `routex.kmp-jvm-library`, which every
    // publishable module applies before this convention.
    id("org.jetbrains.kotlin.multiplatform") apply false
}

group = "tech.yaxi"
version = routexClientVersion().get()

// Bundle the Apache 2.0 LICENSE in every published JAR's META-INF/; Section 4(a)
// of the license requires giving recipients a copy. AAR consumers (Android apps)
// instead get the license via the AAR's POM `<license>` block plus app-level
// aggregators like the OSS Licenses Gradle Plugin; AndroidX itself doesn't
// bundle LICENSE inside its AARs' classes.jar, so we don't either.
tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

// Dokka v2's K2 analyzer can't resolve cross-module project deps from a sibling
// module's `commonMain` metadata klib (see Dokka issues #2679, #3377), so the
// rendered HTML shows `ERROR CLASS: Symbol not found for X` for every type that
// crosses a Gradle project boundary. Workaround: append the JVM target's compile
// classpath to the `commonMain` Dokka source set so every project dep lands as a
// real `.class` jar that the K2 analyzer can resolve. Per-platform source sets
// (jvmMain, androidMain) are suppressed so each declaration appears once.
// Lazy collection of every sibling publishable module's JVM jar. Appended to each
// visible Dokka source set's classpath below so KDoc refs across module boundaries
// (e.g. `[RoutexRefreshClient]` from inside `:client`) resolve in the per-module
// javadoc jar published to Maven Central. Lazy task lookup avoids forcing eager
// evaluation of sibling projects.
val siblingJvmJars =
    rootProject.subprojects
        .filter { it.path != project.path }
        .map { sibling -> sibling.tasks.matching { it.name == "jvmJar" } }

dokka {
    dokkaSourceSets.configureEach {
        perPackageOption {
            matchingRegex.set("""tech\.yaxi\.routex\..*\.internal(\..*)?""")
            suppress.set(true)
        }
        externalDocumentationLinks.register("ktor") {
            url("https://api.ktor.io/")
        }
        externalDocumentationLinks.register("kotlinxSerialization") {
            url("https://kotlinlang.org/api/kotlinx.serialization/")
        }
        externalDocumentationLinks.register("kotlinxCoroutines") {
            url("https://kotlinlang.org/api/kotlinx.coroutines/")
        }
        externalDocumentationLinks.register("kotlinxDatetime") {
            url("https://kotlinlang.org/api/kotlinx-datetime/")
        }
        siblingJvmJars.forEach { classpath.from(it) }
    }
    dokkaSourceSets.named("commonMain") {
        analysisPlatform.set(KotlinPlatform.JVM)
        kotlin.targets.findByName("jvm")?.let { jvm ->
            classpath.from(
                jvm.compilations.named("main").map { it.compileDependencyFiles + it.output.classesDirs },
            )
        }
    }
    // Keep `commonMain` and the JVM-bound chain so JVM-only types (e.g.
    // `RoutexAsyncClient`, `RoutexRefreshAsyncClient`) appear in generated docs.
    // Dokka rejects a configuration where `jvmMain` survives but an intermediate
    // it `dependsOn` (e.g. `:settlement`'s `jvmAndAndroidMain`) is suppressed, so
    // we keep that whole chain visible. Per-platform Apple/Android leaf sets stay
    // suppressed so the pages don't duplicate every common declaration.
    val jvmDocSourceSets = setOf("commonMain", "jvmAndAndroidMain", "jvmMain")
    dokkaSourceSets.matching { it.name !in jvmDocSourceSets }.configureEach {
        suppress.set(true)
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            // Ship per-target sources so IDEs (IntelliJ, Android Studio) attach
            // them automatically when a consumer depends on the published artifact.
            sourcesJar = SourcesJar.Sources(),
        ),
    )
}

mavenPublishing {

    publishToMavenCentral()

    // Release publications must be GPG-signed; SNAPSHOTs publish unsigned and
    // any local `publishToMavenLocal` skips signing too. This makes both the
    // CI snapshot workflow and ad-hoc local publishing work without GPG.
    val versionString = version.toString()
    val isSnapshot = versionString.endsWith("-SNAPSHOT")
    val isLocalPublish =
        gradle.startParameter.taskNames.any { it.endsWith("publishToMavenLocal", ignoreCase = true) }
    if (!isSnapshot && !isLocalPublish) {
        signAllPublications()
    }

    pom {
        name.set("routex-client")
        description.set("Kotlin multiplatform client for YAXI's Open Banking services")
        url.set("https://github.com/yaxitech/routex-client-kotlin/")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("yaxitech")
                name.set("YAXI")
                email.set("hello@yaxi.tech")
                organization.set("YAXI")
                organizationUrl.set("https://yaxi.tech")
            }
        }
        scm {
          url.set("https://github.com/yaxitech/routex-client-kotlin/")
          connection.set("scm:git:git://github.com/yaxitech/routex-client-kotlin.git")
          developerConnection.set("scm:git:ssh://git@github.com/yaxitech/routex-client-kotlin.git")
        }
    }
}

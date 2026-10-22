plugins {
    application
    kotlin("jvm") version "2.4.20"
}

kotlin {
    // Pin the toolchain so a host JDK newer than Kotlin's max target doesn't cause a
    // Java/Kotlin JVM-target mismatch.
    jvmToolchain(25)
}

dependencies {
    implementation("tech.yaxi:routex-client:0.9.0")
    implementation("tech.yaxi:routex-refresh-client:0.9.0")
    implementation("tech.yaxi:routex-tickets:0.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // Silence Ktor's SLF4J "no providers" warning during demo runs.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.20")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "tech.yaxi.refresh.demo.KotlinDemoKt"
}

tasks.test {
    useJUnitPlatform()
}

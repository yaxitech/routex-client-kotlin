plugins {
    application
    kotlin("jvm") version "2.3.21"
}

dependencies {
    implementation("tech.yaxi:routex-client:0.3.0-SNAPSHOT")
    implementation("tech.yaxi:routex-refresh-client:0.3.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Silence Ktor's SLF4J "no providers" warning during demo runs.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "tech.yaxi.refresh.demo.KotlinDemoKt"
}

tasks.test {
    useJUnitPlatform()
}

plugins {
    application
    java
}

dependencies {
    implementation("tech.yaxi:routex-client:0.5.0")
    implementation("tech.yaxi:routex-refresh-client:0.5.0")
    implementation("tech.yaxi:routex-tickets:0.5.0")
    // Silence Ktor's SLF4J "no providers" warning during demo runs.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "tech.yaxi.refresh.demo.JavaDemo"
}

// Demo uses pattern-matched switch on sealed types (JDK 21+).
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

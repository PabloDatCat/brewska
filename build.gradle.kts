plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("io.ktor.plugin") version "3.6.0"
    application
}
repositories { mavenCentral() }
kotlin { jvmToolchain(21) }
application { mainClass.set("com.brewska.MainKt") }
val ktorVersion = "3.6.0"
ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}
dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-routing-openapi:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    implementation("com.typesafe:config:1.4.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("org.flywaydb:flyway-core:11.13.2")
    implementation("org.flywaydb:flyway-database-postgresql:11.13.2")
    implementation("org.postgresql:postgresql:42.7.8")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.testcontainers:postgresql:1.21.3")
}
tasks.test { useJUnitPlatform { excludeTags("integration") } }
val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests against real PostgreSQL (Docker or TEST_DATABASE_URL)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}
tasks.check { dependsOn(integrationTest) }
tasks.wrapper { gradleVersion = "8.14.1"; distributionType = Wrapper.DistributionType.BIN }

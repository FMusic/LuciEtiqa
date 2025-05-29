import com.github.gradle.node.task.NodeTask

val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.1.3"
    id("com.github.node-gradle.node") version "3.5.1"
}

node {
    version.set("22.13.1")
    download.set(true)
    nodeProjectDir.set(projectDir)
}

group = "mjuzik.le"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-compression")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-thymeleaf")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    // H2 for dev
    runtimeOnly("com.h2database:h2:2.1.214")
    // Postgres for prod
    runtimeOnly("org.postgresql:postgresql:42.5.4")

}

tasks.register<NodeTask>("tailwindBuild") {
    group = "build"
    dependsOn("npmInstall")         // ensure node_modules is installed first
    workingDir.set(projectDir)     // run in project root

    // point at the standalone CLI entrypoint
    script.set(file("node_modules/@tailwindcss/cli/dist/index.mjs"))

    // these become the process args after the script
    args.set(listOf(
        "-i", "src/main/resources/static/css/tailwind.css",
        "-o", "src/main/resources/static/css/styles.css",
        "--minify"
    ))
}

tasks.named("processResources") {
    dependsOn("tailwindBuild")
}

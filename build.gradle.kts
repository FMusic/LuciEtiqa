import com.github.gradle.node.task.NodeTask

val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.1.3"
    kotlin("plugin.serialization") version "2.1.10"
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
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-sessions")
    implementation("org.mindrot:jbcrypt:0.4")           // password hashing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.41.1")
    // H2 for dev
    runtimeOnly("com.h2database:h2:2.1.214")
    // Postgres for prod
    implementation("org.postgresql:postgresql:42.5.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

}

tasks.register<NodeTask>("tailwindBuild") {
    group = "build"
    dependsOn("npmInstall")
    workingDir.set(projectDir)

    script.set(file("node_modules/@tailwindcss/cli/dist/index.mjs"))
    args.set(
        listOf(
            "-c", "tailwind.config.js",                                // <── NEW
            "-i", "src/main/resources/static/css/tailwind.css",
            "-o", "src/main/resources/static/css/styles.css",
            "--minify"
        )
    )
}

tasks.register<Exec>("tailwindWatchCli") {
    group = "development"
    dependsOn("npmInstall")

    standardInput = System.`in`

    commandLine(
        "npx", "tailwindcss",
        "-c", "tailwind.config.js",
        "-i", "src/main/resources/static/css/tailwind.css",
        "-o", "src/main/resources/static/css/styles.css",
        "--watch", "--poll"        // --poll is still helpful on Windows
    )

    // Keeps the process alive (Exec *does* have this property)
    standardInput = System.`in`
}

tasks.register<Exec>("browserSync") {
    group = "development"
    commandLine("npm", "run", "livereload")
}

tasks.withType<JavaExec> {
    // Enables auto-reloading in dev with: ./gradlew run --continuous
    jvmArgs = listOf("-Dio.ktor.development=true")
    standardInput = System.`in` // forward input for console input
}

tasks.named("processResources") {
    dependsOn("tailwindBuild")
}

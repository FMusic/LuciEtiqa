package mjuzik.le

import io.ktor.server.application.Application
import mjuzik.le.config.configureAuth
import mjuzik.le.config.configureDatabase
import mjuzik.le.config.configureHTTP
import mjuzik.le.config.configureMonitoring
import mjuzik.le.config.configureSerialization
import mjuzik.le.routing.configureRouting
import org.thymeleaf.TemplateEngine

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val env = environment.config.propertyOrNull("ktor.deployment.environment")?.getString() ?: "dev"
    val thymeleafVersion = TemplateEngine::class.java.`package`.implementationVersion
    println("Starting application in environment: $env Thymeleaf version: $thymeleafVersion")
    configureHTTP(env)
    configureMonitoring()
    configureSerialization()
    configureAuth()
    configureRouting()
    configureDatabase(env)
}

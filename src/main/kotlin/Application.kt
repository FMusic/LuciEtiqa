package mjuzik.le

import io.ktor.server.application.*
import mjuzik.le.config.configureAuth
import mjuzik.le.config.configureDatabase
import mjuzik.le.config.configureHTTP
import mjuzik.le.config.configureMonitoring
import mjuzik.le.config.configureSerialization
import mjuzik.le.view.configureRouting

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureAuth()
    configureRouting()
    configureDatabase()
}

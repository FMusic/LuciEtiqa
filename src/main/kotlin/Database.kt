package mjuzik.le

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase() {
    val env = environment.config.propertyOrNull("ktor.deployment.environment")?.getString() ?: "dev"
    val dsConfig = environment.config.config("environments.$env.datasource")
    val config = HikariConfig().apply {
        driverClassName = dsConfig.property("driver").getString()
        jdbcUrl = dsConfig.property("url").getString()
        username = dsConfig.property("user").getString()
        password = dsConfig.property("password").getString()
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    }
    val ds = HikariDataSource(config)
    Database.connect(ds)
}
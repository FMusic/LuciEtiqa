package mjuzik.le.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import mjuzik.le.domain.DomainTables
import mjuzik.le.domain.seedFakeDataIfDev
import mjuzik.le.domain.seedProdInitialData
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase(env: String) {
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
    transaction {
        SchemaUtils.createMissingTablesAndColumns(*DomainTables.all)
    }
    if(env=="dev")  {
        seedFakeDataIfDev(env)
        seedProdInitialData()
    }
    else {
        seedProdInitialData()
    }
}
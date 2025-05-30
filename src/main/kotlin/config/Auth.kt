package mjuzik.le.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.*
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import mjuzik.le.domain.UserEntity
import mjuzik.le.util.UUIDSerializer
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Application.configureAuth() {
    install(Sessions) {
        cookie<UserSession>("SESSION") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
        }
    }

    /** SESSION-based authentication */
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                transaction { UserEntity.findById(session.userId) }?.let { session }
            }
            challenge {
                call.respondRedirect("/")   // kick anonymous users to landing
            }
        }
    }
}

@Serializable
data class UserSession(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID
)
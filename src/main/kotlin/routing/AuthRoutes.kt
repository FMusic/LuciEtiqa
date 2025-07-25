package mjuzik.le.routing

import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import mjuzik.le.config.UserSession
import mjuzik.le.domain.UserEntity
import mjuzik.le.domain.Users
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import kotlin.text.trim

fun Route.authRoutes() {
    post("/login") {
        val p = call.receiveParameters()
        val email = p["email"]?.trim() ?: ""
        val password = p["password"] ?: ""

        val user = transaction {
            UserEntity.find { Users.email eq email }
                .singleOrNull()
                ?.takeIf { BCrypt.checkpw(password, it.passwordHash) }
        }

        if (user != null) {
            call.sessions.set(UserSession(user.id.value))
            if (user.role == mjuzik.le.domain.UserRole.ADMIN) {
                call.respondRedirect("/admin/users")
            } else {
                call.respondRedirect("/wines")
            }
        } else {
            call.respondRedirect("/?error=badcredentials")
        }
    }

    post("/logout") {
        call.sessions.clear<UserSession>()
        call.respondRedirect("/")
    }
}

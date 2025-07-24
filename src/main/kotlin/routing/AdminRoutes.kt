package mjuzik.le.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.server.sessions.*
import io.ktor.server.request.receiveParameters
import mjuzik.le.config.UserSession
import mjuzik.le.domain.UserEntity
import mjuzik.le.domain.UserRole
import mjuzik.le.domain.WineEntity
import mjuzik.le.domain.Wines
import mjuzik.le.domain.Users
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*

fun Route.adminRoutes() {
    fun isAdmin(call: ApplicationCall): Boolean {
        val session = call.sessions.get<UserSession>() ?: return false
        return transaction {
            UserEntity.findById(session.userId)?.role == UserRole.ADMIN
        }
    }

    get("/admin/users") {
        if (!isAdmin(call)) return@get call.respondRedirect("/wines")
        val users = transaction { UserEntity.all().toList() }
        call.respond(
            ThymeleafContent(
                "layout",
                mapOf(
                    "title" to "All Users",
                    "content" to "admin_users",
                    "users" to users,
                    "isAdmin" to true
                )
            )
        )
    }

    get("/admin/users/{userId}") {
        if (!isAdmin(call)) return@get call.respondRedirect("/wines")
        val userId = call.parameters["userId"] ?: return@get call.respondRedirect("/admin/users")
        val user = transaction { UserEntity.findById(UUID.fromString(userId)) }
        if (user == null) return@get call.respondRedirect("/admin/users")
        val wines = transaction { WineEntity.find { Wines.owner eq user.id }.toList() }
        call.respond(
            ThymeleafContent(
                "layout",
                mapOf(
                    "title" to "User Wines",
                    "content" to "admin_user_wines",
                    "user" to user,
                    "wines" to wines,
                    "isAdmin" to true
                )
            )
        )
    }

    get("/admin/users/new") {
        if (!isAdmin(call)) return@get call.respondRedirect("/wines")
        val error = call.request.queryParameters["error"] ?: ""
        call.respond(
            ThymeleafContent(
                "layout",
                mapOf(
                    "title" to "Add User",
                    "content" to "admin_user_new",
                    "isAdmin" to true,
                    "error" to error
                )
            )
        )
    }

    post("/admin/users/new") {
        if (!isAdmin(call)) return@post call.respondRedirect("/wines")
        val params = call.receiveParameters()
        val email = params["email"]?.trim() ?: ""
        val password = params["password"] ?: ""
        val role = params["role"] ?: "USER"
        if (email.isNotBlank() && password.isNotBlank()) {
            transaction {
                UserEntity.new {
                    this.email = email
                    this.passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
                    this.role = if (role == "ADMIN") UserRole.ADMIN else UserRole.SPECIAL
                }
            }
            call.respondRedirect("/admin/users")
        } else {
            call.respondRedirect("/admin/users/new?error=missing")
        }
    }
}

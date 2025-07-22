package mjuzik.le.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.server.sessions.*
import mjuzik.le.config.UserSession
import mjuzik.le.domain.UserEntity
import mjuzik.le.domain.UserRole
import mjuzik.le.domain.WineEntity
import mjuzik.le.domain.Wines
import org.jetbrains.exposed.sql.transactions.transaction
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
                    "content" to "admin_users :: content",
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
                    "content" to "admin_user_wines :: content",
                    "user" to user,
                    "wines" to wines,
                    "isAdmin" to true
                )
            )
        )
    }
}

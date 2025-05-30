package mjuzik.le.view

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.thymeleaf.ThymeleafContent
import mjuzik.le.config.UserSession
import mjuzik.le.domain.UserEntity
import mjuzik.le.domain.Users
import mjuzik.le.domain.WineEntity
import mjuzik.le.domain.Wines
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import org.jetbrains.exposed.sql.SortOrder


fun Application.configureRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        staticResources("/static", "/static")

        // Serve index.html as the root ("/")
        get("/") {
            call.respondRedirect("/static/index.html")
        }

        post("/login") {
            val p = call.receiveParameters()
            val email = p["email"]?.trim() ?: ""
            val password = p["password"] ?: ""

            val userId: UUID? = transaction {
                UserEntity.find { Users.email eq email }
                    .singleOrNull()
                    ?.takeIf { BCrypt.checkpw(password, it.passwordHash) }
                    ?.id?.value
            }

            if (userId != null) {
                call.sessions.set(UserSession(userId))
                call.respondRedirect("/wines")
            } else {
                call.respondRedirect("/?error=badcredentials")
            }
        }

        authenticate("auth-session") {

            get("/wines") {
                val session = call.principal<UserSession>()!!

                /* Fetch wines for that user */
                val wines = transaction {
                    WineEntity
                        .find { Wines.owner eq session.userId }
                        .orderBy(Wines.createdAt to SortOrder.DESC)
                        .map { it }                            // DAO list
                }

                call.respond(
                    ThymeleafContent(
                        "layout",
                        mapOf(
                            "title" to "My Wines",
                            "wines" to wines
                        )
                    )
                )

            }
        }
    }
}

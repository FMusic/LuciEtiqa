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
import mjuzik.le.domain.WineVersionEntity
import mjuzik.le.domain.Wines
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import org.jetbrains.exposed.sql.SortOrder
import java.math.BigDecimal


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
                            "content" to "wines :: content",
                            "wines" to wines
                        )
                    )
                )
            }
            get("/wines/new") {
                call.respond(
                    ThymeleafContent(
                        "layout",
                        mapOf(
                            "title" to "Add Wine",
                            "content" to "new-wine :: content"
                        )
                    )
                )
            }
            get("/wines/new") {
                call.respond(
                    ThymeleafContent(
                        "layout",
                        mapOf(
                            "title" to "Add Wine",
                            "content" to "wine_form :: content"
                        )
                    )
                )
            }

            post("/wines") {
                val session = call.principal<UserSession>()!!
                val p = call.receiveParameters()

                /* ---------- helpers ------------------------------------------- */
                fun String?.trimOrBlank() = this?.trim().orEmpty()
                fun String?.toBD() = this?.trim()?.takeIf { it.isNotBlank() }?.toBigDecimal()
                    ?: BigDecimal.ZERO

                fun String?.toBDorNull() = this?.trim()?.takeIf { it.isNotBlank() }?.toBigDecimal()

                /* ---------- core wine fields ---------------------------------- */
                val name = p["name"].trimOrBlank()
                val vintage = p["vintage"].trimOrBlank()
                val category = p["category"].trimOrBlank()
                val origin = p["origin"].trimOrBlank()

                if (name.isBlank() || vintage.isBlank() || category.isBlank()) {
                    call.respondRedirect("/wines/new?error=missing")
                    return@post
                }

                /* ---------- nutrition & extras -------------------------------- */
                val energyKj = p["energyKj"].toBD()
                val energyKcal = p["energyKcal"].toBD()
                val fat = p["fat"].toBD()
                val saturates = p["saturates"].toBD()
                val carbohydrate = p["carbohydrate"].toBD()
                val sugars = p["sugars"].toBD()
                val protein = p["protein"].toBD()
                val salt = p["salt"].toBD()

                val ingredients = p["ingredients"].trimOrBlank()
                val allergens = p["allergens"].trimOrBlank()
                val additives = p["additives"].trimOrBlank()

                val abv = p["abv"].toBD()
                val sugarGpl = p["sugarGpl"].toBD()
                val servingTempC = p["servingTempC"].toBDorNull()
                val tastingNotes = p["tastingNotes"].trimOrBlank().ifBlank { null }

                /* ---------- persist ------------------------------------------- */
                transaction {
                    val owner = UserEntity[session.userId]

                    val wine = WineEntity.new(UUID.randomUUID()) {
                        this.owner = owner
                        this.name = name
                        this.vintage = vintage
                        this.category = category
                        this.origin = origin.ifBlank { null }
                    }

                    WineVersionEntity.new(UUID.randomUUID()) {
                        this.wine = wine
                        this.versionTag = "v1"
                        this.energyKj = energyKj
                        this.energyKcal = energyKcal
                        this.fat = fat
                        this.saturates = saturates
                        this.carbohydrate = carbohydrate
                        this.sugars = sugars
                        this.protein = protein
                        this.salt = salt

                        this.ingredients = ingredients
                        this.allergens = allergens
                        this.additives = additives
                        this.abv = abv
                        this.sugarGpl = sugarGpl
                        this.servingTempC = servingTempC
                        this.tastingNotes = tastingNotes
                    }
                }

                call.respondRedirect("/wines")
            }
        }
    }
}

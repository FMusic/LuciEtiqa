package mjuzik.le.routing

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent
import mjuzik.le.config.UserSession
import mjuzik.le.domain.QrLinkEntity
import mjuzik.le.domain.QrLinks
import mjuzik.le.domain.UserEntity
import mjuzik.le.domain.WineEntity
import mjuzik.le.domain.WineLabelEntity
import mjuzik.le.domain.Wines
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import org.jetbrains.exposed.sql.SortOrder

fun Route.wineRoutes(){
    authenticate("auth-session") {

        get("/wines") {
            val session = call.principal<UserSession>()!!
            val (wines, qrMap) = transaction {
                val list = WineEntity
                    .find { Wines.owner eq session.userId }
                    .orderBy(Wines.createdAt to SortOrder.DESC)
                    .toList()

                // Routing.kt  – /wines list route
                val qr: Map<UUID,String?> = list.associate { wine ->
                    val slug = wine.versions.maxByOrNull { it.createdAt }?.let { latest ->
                        QrLinkEntity.find { QrLinks.wineVersion eq latest.id }
                            .firstOrNull()
                            ?.code
                    }
                    wine.id.value /* UUID */ to slug        // ← keep UUID, not EntityID
                }

                list to qr
            }

            call.respond(
                ThymeleafContent(
                    "layout",
                    mapOf(
                        "title" to "My Wines",
                        "content" to "wines :: content",
                        "wines" to wines,
                        "qrCodes" to qrMap        // ← new
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
                        "content" to "wine :: content"
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

            val (qrSlug, wineId) = transaction {
                val owner = UserEntity[session.userId]
                val wine = WineEntity.new(UUID.randomUUID()) {
                    this.owner = owner
                    this.name = name
                    this.vintage = vintage
                    this.category = category
                    this.origin = origin.ifBlank { null }
                }
                val newWineLabel = WineLabelEntity.new(UUID.randomUUID()) {
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

                val slug = UUID.randomUUID().toString().substring(0, 8)
                QrLinkEntity.new(UUID.randomUUID()) {
                    wineVersion = newWineLabel
                    code = slug
                }
                slug to wine.id.value        // we’ll need both outside
            }

            call.respond(
                ThymeleafContent(
                    "layout",
                    mapOf(
                        "title" to "Add Wine",
                        "content" to "wine :: content",
                        "qrCode" to qrSlug,               // ↓ used by wine.html
                        "wineId" to wineId                // handy if you want a link
                    )
                )
            )
        }
    }
}
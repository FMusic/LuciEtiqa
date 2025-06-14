package mjuzik.le.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.thymeleaf.ThymeleafContent
import mjuzik.le.config.UserSession
import mjuzik.le.domain.WineEntity
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

fun Route.wineEditRoutes(){
    route("/wines/{wineId}") {

        /** 1️⃣  Render “edit wine” form (prefilled) */
        get("/edit") {
            val session = call.principal<UserSession>()!!

            val wineUuid = call.parameters["wineId"]
                ?.let { try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null } }
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            /* ---- fetch wine and latest label ---- */
            val (wine, latestLabel) = transaction {
                val w = WineEntity[wineUuid]

                /* 403 if not the owner (or collaborator with canEdit=true if you add that) */
                if (w.owner.id.value != session.userId) error("Forbidden")

                val v = w.versions.maxByOrNull { it.createdAt }!!
                w to v
            }

            val categories = listOf(
                "Red", "White", "Rosé",
                "Sparkling", "Dessert", "Fortified"
            )

            call.respond(
                ThymeleafContent(
                    "layout",
                    mapOf(
                        "title" to "Edit ${wine.name}",
                        "content" to "wine_edit :: content",
                        "wine" to wine,
                        "version" to latestLabel,
                        "categories" to categories
                    )
                )
            )
        }

        /** 2️⃣  Handle “save changes”  or  “delete”  from the form */
        post {
            val session  = call.principal<UserSession>()!!

            val wineUuid = call.parameters["wineId"]
                ?.let { try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null } }
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val form = call.receiveParameters()
            val method = form["_method"]?.lowercase() ?: "post"   // put | delete | post

            /* ------------ tiny helpers reused from /wines POST ------------ */
            fun String?.trimOrBlank() = this?.trim().orEmpty()
            fun String?.toBD()        = this?.trim()
                ?.takeIf { it.isNotBlank() }?.toBigDecimal()
                ?: BigDecimal.ZERO
            fun String?.toBDorNull()  = this?.trim()
                ?.takeIf { it.isNotBlank() }?.toBigDecimal()

            transaction {
                val wine = WineEntity[wineUuid]
                if (wine.owner.id.value != session.userId) error("Forbidden")

                when (method) {

                    /* ---------- UPDATE (PUT) ---------- */
                    "put" -> {
                        /* -- update master wine row -- */
                        wine.apply {
                            name     = form["name"].trimOrBlank()
                            vintage  = form["vintage"].trimOrBlank()
                            category = form["category"].trimOrBlank()
                            origin   = form["origin"].trimOrBlank().ifBlank { null }
                        }

                        /* -- mutate latest label in-place -- */
                        val v = wine.versions.maxByOrNull { it.createdAt }!!
                        v.apply {
                            energyKj      = form["energyKj"].toBD()
                            energyKcal    = form["energyKcal"].toBD()
                            fat           = form["fat"].toBD()
                            saturates     = form["saturates"].toBD()
                            carbohydrate  = form["carbohydrate"].toBD()
                            sugars        = form["sugars"].toBD()
                            protein       = form["protein"].toBD()
                            salt          = form["salt"].toBD()

                            ingredients   = form["ingredients"].trimOrBlank()
                            allergens     = form["allergens"].trimOrBlank()
                            additives     = form["additives"].trimOrBlank()

                            abv           = form["abv"].toBD()
                            sugarGpl      = form["sugarGpl"].toBD()
                            servingTempC  = form["servingTempC"].toBDorNull()
                            tastingNotes  = form["tastingNotes"].trimOrBlank().ifBlank { null }
                        }
                    }

                    /* ---------- DELETE ---------- */
                    "delete" -> {
                        /* hard delete; change to soft-delete if needed */
                        wine.delete()      // cascades to labels + qr via FK
                    }

                    else -> error("Unsupported _method=$method")
                }
            }

            /* -- redirect after DB work -- */
            when (method) {
                "put"    -> call.respondRedirect("/wines/$wineUuid/edit?saved=1")
                "delete" -> call.respondRedirect("/wines")
                else     -> call.respondRedirect("/wines")
            }
        }
    }
}

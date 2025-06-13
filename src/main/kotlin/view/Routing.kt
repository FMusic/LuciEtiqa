package mjuzik.le.view

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.http.content.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.sessions.clear
import io.ktor.server.thymeleaf.ThymeleafContent
import mjuzik.le.config.UserSession
import mjuzik.le.domain.QrLinkEntity
import mjuzik.le.domain.QrLinks
import mjuzik.le.domain.UserEntity
import mjuzik.le.domain.Users
import mjuzik.le.domain.WineEntity
import mjuzik.le.domain.WineLabelEntity
import mjuzik.le.domain.Wines
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import org.jetbrains.exposed.sql.SortOrder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import javax.imageio.ImageIO


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

        /** Public digital-label page */
        get("/wines/{wineId}") {
            val idParam = call.parameters["wineId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val (wine, version) = transaction {
                val wine = WineEntity[UUID.fromString(idParam)]

                // pick the newest published version, or fall back to latest draft
                val version = wine.versions
                    .sortedByDescending { it.createdAt }
                    .firstOrNull { it.isPublished } ?: wine.versions.maxBy { it.createdAt }

                wine to version
            }

            call.respond(
                ThymeleafContent(
                    "layout",
                    mapOf(
                        "title"   to "${wine.name} – Digital label",
                        "content" to "wine_label :: content",   // ← fragment name
                        "wine"    to wine,
                        "v"       to version                    // shorter handle in template
                    )
                )
            )
        }


        post("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }

        get("/l/{code}") {
            val code = call.parameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val wineId = transaction {
                QrLinkEntity.find { QrLinks.code eq code }
                    .firstOrNull()
                    ?.wineVersion
                    ?.wine
                    ?.id?.value
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respondRedirect("/wines/$wineId")
        }

        get("/qr/{code}.png") {
            val code = call.parameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val size = 400                                        // px
            val targetUrl = "${call.request.origin.scheme}://${call.request.host()}:${call.request.port()}/l/$code"

            val matrix = QRCodeWriter().encode(targetUrl, BarcodeFormat.QR_CODE, size, size)
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until size) for (y in 0 until size)
                img.setRGB(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())

            val baos = ByteArrayOutputStream()
            ImageIO.write(img, "png", baos)
            call.respondBytes(baos.toByteArray(), ContentType.Image.PNG)
        }

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
                            "title"    to "My Wines",
                            "content"  to "wines :: content",
                            "wines"    to wines,
                            "qrCodes"  to qrMap        // ← new
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
                            "title"   to "Add Wine",
                            "content" to "wine :: content",
                            "qrCode"  to qrSlug,               // ↓ used by wine.html
                            "wineId"  to wineId                // handy if you want a link
                        )
                    )
                )
            }
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
                                "title"      to "Edit ${wine.name}",
                                "content"    to "wine_edit :: content",
                                "wine"       to wine,
                                "version"    to latestLabel,
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
    }
}


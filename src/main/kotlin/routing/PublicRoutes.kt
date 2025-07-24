package mjuzik.le.routing

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.thymeleaf.ThymeleafContent
import mjuzik.le.domain.QrLinkEntity
import mjuzik.le.domain.QrLinks
import mjuzik.le.domain.WineEntity
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

fun Route.publicRoutes(){

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
                    "title" to "${wine.name} – Digital label",
                    "content" to "wine_label",   // ← fragment name
                    "wine" to wine,
                    "v" to version                    // shorter handle in template
                )
            )
        )
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
}

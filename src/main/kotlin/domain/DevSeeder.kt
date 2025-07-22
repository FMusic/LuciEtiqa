package mjuzik.le.domain

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*
fun seedFakeDataIfDev(env: String) {
    if (env != "dev") return

    transaction {
        val email = "user@example.com"
        val existing = UserEntity.find { Users.email eq email }.firstOrNull()
        if (existing != null) return@transaction   // already seeded

        /* ---------- demo user ------------------------------------------------ */
        val passwordHash = BCrypt.hashpw("password", BCrypt.gensalt())
        val user = UserEntity.new(UUID.randomUUID()) {
            this.email = email
            this.passwordHash = passwordHash
            this.role = UserRole.REGULAR
            this.createdAt = Clock.System.now()
        }

        /* ---------- three “empty” wines (as before) -------------------------- */
        repeat(3) { i ->
            WineEntity.new(UUID.randomUUID()) {
                this.owner = user
                this.name = "Cabernet Demo $i"
                this.vintage = "20${20 + i}"
                this.category = "Red"
            }
        }

        /* ---------- fully-fledged sample wine -------------------------------- */
        val sampleWine = WineEntity.new(UUID.randomUUID()) {
            owner = user
            name = "Sample Cabernet Sauvignon"
            vintage = "2023"
            category = "Red"
            origin = "PDO Kutjevo"
        }

        val sampleVersion = WineLabelEntity.new(UUID.randomUUID()) {
            wine = sampleWine
            versionTag = "v1"
            isPublished = true                         // ← important for public page

            /* EU 2021/2117 mandatory nutrition (per 100 ml) */
            energyKj      = "259.0".toBigDecimal()
            energyKcal    = "62.0".toBigDecimal()
            fat           = "0.0".toBigDecimal()
            saturates     = "0.0".toBigDecimal()
            carbohydrate  = "3.2".toBigDecimal()
            sugars        = "0.2".toBigDecimal()
            protein       = "0.0".toBigDecimal()
            salt          = "0.0".toBigDecimal()

            /* Composition & extras */
            ingredients   = "Wine (grapes), water, yeast nutrients"
            allergens     = "<strong>Sulphites</strong>"
            additives     = """[{"category":"Preservative","eNumber":"E220"}]"""
            abv           = "12.5".toBigDecimal()
            sugarGpl      = "3.4".toBigDecimal()
            servingTempC  = "16.0".toBigDecimal()
            tastingNotes  = "Deep ruby colour with aromas of blackcurrant and cedar.\nSilky tannins, medium body and a lingering finish."
        }

        /* optional—but handy while testing: create a QR slug */
        QrLinks.insert {
            it[id]           = UUID.randomUUID()
            it[wineVersion]  = sampleVersion.id
            it[code]         = "DEMOQR01"
            it[createdAt]    = Clock.System.now()
        }
    }

    println("""Dev database seeded.
    • Email:    user@example.com
    • Password: password
    • Wines:    3 minimal + 1 fully populated (QR ⟶ DEMOQR01)""")

}

fun seedProdInitialData(){
    val adminEmail = "admin@mjuzik.dev"
    transaction {
        val adminExists = UserEntity.find { Users.email eq adminEmail }.firstOrNull()
        if (adminExists == null) {
            val adminPasswordHash = BCrypt.hashpw("admin", BCrypt.gensalt())
            val user: UserEntity = UserEntity.new(UUID.randomUUID()) {
                this.email = adminEmail
                this.passwordHash = adminPasswordHash
                this.role = UserRole.ADMIN
                this.createdAt = Clock.System.now()
            }
        }
    }
    println("Production database seeded with initial admin user: $adminEmail")
}

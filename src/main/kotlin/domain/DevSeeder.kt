package mjuzik.le.domain

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*

fun seedFakeDataIfDev(env: String) {
    if (env != "dev") return

    transaction {
        val email = "user@example.com"
        val existing = UserEntity.find { Users.email eq email }.firstOrNull()
        if (existing != null) return@transaction // Already seeded

        val passwordHash = BCrypt.hashpw("password", BCrypt.gensalt())

        val user = UserEntity.new(UUID.randomUUID()) {
            this.email = email
            this.passwordHash = passwordHash
            this.role = UserRole.REGULAR
            this.createdAt = Clock.System.now()
        }

        repeat(3) { i ->
            WineEntity.new(UUID.randomUUID()) {
                this.owner = user
                this.name = "Cabernet Demo $i"
                this.vintage = "20${20 + i}"
                this.category = "Red"
            }
        }
    }

    println("Dev database seeded with demo user and wines")
}

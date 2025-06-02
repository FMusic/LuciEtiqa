package mjuzik.le.domain

import java.util.UUID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Clock


/* ---------- ENUMS -------------------------------------------------------- */

enum class UserRole { ADMIN, REGULAR, SPECIAL }
enum class LicenseStatus { ACTIVE, EXPIRED, CANCELLED }

/* ---------- USERS & ROLES ------------------------------------------------ */

object Users : UUIDTable("users") {
    val email = varchar("email", 254).uniqueIndex()
    val passwordHash = varchar("password_hash", 72)     // bcrypt hash
    val role = enumerationByName("role", 16, UserRole::class)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(Users)

    var email by Users.email
    var passwordHash by Users.passwordHash
    var role by Users.role
    var createdAt by Users.createdAt
}

/* ---------- WINE & VERSIONING ------------------------------------------- */

object Wines : UUIDTable("wines") {
    val owner = reference("owner_id", Users)
    val name = varchar("name", 120)
    val vintage = varchar("vintage", 6)               // e.g. “2021”
    val category = varchar("category", 40)  // Still, Sparkling…
    val origin = varchar("origin", 120).nullable() // e.g. “PDO Rioja”
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

object WineLabels : UUIDTable("wine_versions") {
    val wine = reference("wine_id", Wines)
    val versionTag = varchar("version_tag", 32)       // “v1”, “2023-05-fix”
    val isPublished = bool("published").default(false)
    val createdAt = timestamp("created_at").default(Clock.System.now())

    /* --- EU 2021/2117 mandatory fields (per 100 ml) --------------------- */
    val energyKj = decimal("energy_kj", 6, 1) // e.g.  259.0
    val energyKcal = decimal("energy_kcal", 6, 1) // e.g.   62.0
    val fat = decimal("fat", 5, 2)
    val saturates = decimal("saturates", 5, 2)
    val carbohydrate = decimal("carbohydrate", 5, 2)
    val sugars = decimal("sugars", 5, 2)
    val protein = decimal("protein", 5, 2)
    val salt = decimal("salt", 5, 2)

    /* --- Everything else ------------------------------------------------ */
    val ingredients = text("ingredients").nullable()            // ordered list
    val allergens = text("allergens").nullable()              // emphasised on label
    val additivesJson = text("additives_json").default("{}")         // optional JSON blob
    val jsonPayload = text("render_json").default("{}")            // entire label as JSON

    val abv = decimal("abv", 4, 1)                    // 12.5 % vol
    val sugarGpl = decimal("sugar_gpl", 6, 1)              // g per L
    val servingTempC = decimal("serving_temp_c", 4, 1).nullable()
    val tastingNotes = text("tasting_notes").nullable()
}

class WineEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<WineEntity>(Wines)

    var owner by UserEntity referencedOn Wines.owner
    var name by Wines.name
    var vintage by Wines.vintage
    var category by Wines.category
    var origin by Wines.origin
    val versions by WineLabelEntity referrersOn WineLabels.wine
}

class WineLabelEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<WineLabelEntity>(WineLabels)

    var wine by WineEntity referencedOn WineLabels.wine
    var versionTag by WineLabels.versionTag
    var isPublished by WineLabels.isPublished
    var createdAt by WineLabels.createdAt

    /* nutrition & ingredients fields exposed as properties */
    var energyKj by WineLabels.energyKj
    var energyKcal by WineLabels.energyKcal
    var fat by WineLabels.fat
    var saturates by WineLabels.saturates
    var carbohydrate by WineLabels.carbohydrate
    var sugars by WineLabels.sugars
    var protein by WineLabels.protein
    var salt by WineLabels.salt
    var ingredients by WineLabels.ingredients
    var allergens by WineLabels.allergens
    var additives by WineLabels.additivesJson
    var jsonPayload by WineLabels.jsonPayload
    var abv by WineLabels.abv
    var sugarGpl by WineLabels.sugarGpl
    var servingTempC by WineLabels.servingTempC
    var tastingNotes by WineLabels.tastingNotes
}

/* ---------- QR LINKS ----------------------------------------------------- */

object QrLinks : UUIDTable("qr_links") {
    val wineVersion = reference("version_id", WineLabels).uniqueIndex()
    val code = varchar("code", 12).uniqueIndex()   // short slug in URL
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

class QrLinkEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<QrLinkEntity>(QrLinks)

    var wineVersion by WineLabelEntity referencedOn QrLinks.wineVersion
    var code by QrLinks.code
    var createdAt by QrLinks.createdAt
}

/* ---------- COLLABORATION (Many-to-Many) -------------------------------- */

object WineCollaborators : UUIDTable("wine_collaborators") {
    val wine = reference("wine_id", Wines)
    val user = reference("user_id", Users)
    val canEdit = bool("can_edit").default(false)

    init {
        uniqueIndex(wine, user)
    }
}

/* ---------- LICENSE & SUBSCRIPTION -------------------------------------- */

object Licenses : UUIDTable("licenses") {
    val user = reference("user_id", Users).uniqueIndex()
    val status = enumerationByName("status", 12, LicenseStatus::class)
    val validUntil = timestamp("valid_until")
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

object Subscriptions : UUIDTable("subscriptions") {
    val license = reference("license_id", Licenses).uniqueIndex()
    val stripeCustomerId = varchar("stripe_cust_id", 48)
    val stripeSubscription = varchar("stripe_sub_id", 48)
    val plan = varchar("plan", 24)          // “monthly”, “annual”
    val nextBilling = timestamp("next_billing")
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

/* ---------- AGGREGATE ---------------------------------------------------- */

object DomainTables {
    /** Keep **one** source-of-truth list so migrations become trivial */
    val all = arrayOf(
        Users, Wines, WineLabels, QrLinks,
        WineCollaborators, Licenses, Subscriptions
    )
}

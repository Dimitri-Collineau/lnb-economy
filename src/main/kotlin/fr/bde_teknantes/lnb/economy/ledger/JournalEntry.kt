package fr.bde_teknantes.lnb.economy.ledger

import com.google.gson.JsonObject
import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.warden.common.api.ApiClient

/**
 * A call the server decided to make to the site and the site hasn't confirmed or refused yet: a POST to [route], under
 * `/api/economy/server`, with [body], which carries its `idempotencyKey`.
 *
 * [purpose] names the [TransferJournal.Handler] that finishes the job (gives items, tells the players...), and [data]
 * holds what it needs, so the job can finish after a restart.
 */
data class JournalEntry(
    val body: JsonObject,
    val purpose: String,
    val data: Map<String, String> = emptyMap(),
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val createdAt: Long = 0,
    val lastError: String? = null,
    /** Null in entries written when the journal only sent transfers. Read it through [route]. */
    val path: String? = null,
    /** `cents` once the body's money fields are cents. Null in entries written before 0.3.0, in whole LNBits. */
    val units: String? = null,
) {
    val key: String get() = body.get(KEY).asString

    val route: String get() = path ?: TRANSFERS

    /** The transfer this entry sends, or null when it goes to another route. */
    val transfer: TransferBody?
        get() = if (route == TRANSFERS) ApiClient.GSON.fromJson(body, TransferBody::class.java) else null

    /** The amount in the body, when it has one, for operators' listings. */
    val amount: Long? get() = body.get("amount")?.takeIf { it.isJsonPrimitive }?.asLong

    /**
     * This entry with its money in cents. Entries written before cents (no [units]) hold whole LNBits: their money
     * fields are multiplied by 100, once, before anything is sent again. Sent with the units header, a body left in
     * whole LNBits would move a hundredth of the money, or be refused as another request under the same key.
     */
    fun inCents(): JournalEntry {
        if (units == CENTS) return this
        val converted = body.deepCopy()
        for (field in MONEY_FIELDS) {
            val value = converted.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: continue
            converted.addProperty(field, Math.multiplyExact(value.asLong, 100L))
        }
        return copy(body = converted, units = CENTS)
    }

    companion object {
        const val CENTS = "cents"

        /** The body fields that hold LNBits, on every route the journal sends to. */
        private val MONEY_FIELDS = listOf("amount", "price", "expectedLotPrice")

        const val KEY = "idempotencyKey"
        const val TRANSFERS = "/transfers"

        /** An entry for a POST of [body] to [route]. [body] is turned into JSON as it will be sent. */
        fun of(route: String, body: Any, purpose: String, data: Map<String, String> = emptyMap()): JournalEntry {
            val json = ApiClient.GSON.toJsonTree(body).asJsonObject
            require(json.has(KEY)) { "A journal entry's body needs an $KEY" }
            return JournalEntry(json, purpose, data, path = route, units = CENTS)
        }
    }
}

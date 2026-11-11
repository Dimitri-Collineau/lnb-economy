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
) {
    val key: String get() = body.get(KEY).asString

    val route: String get() = path ?: TRANSFERS

    /** The transfer this entry sends, or null when it goes to another route. */
    val transfer: TransferBody?
        get() = if (route == TRANSFERS) ApiClient.GSON.fromJson(body, TransferBody::class.java) else null

    /** The amount in the body, when it has one, for operators' listings. */
    val amount: Long? get() = body.get("amount")?.takeIf { it.isJsonPrimitive }?.asLong

    companion object {
        const val KEY = "idempotencyKey"
        const val TRANSFERS = "/transfers"

        /** An entry for a POST of [body] to [route]. [body] is turned into JSON as it will be sent. */
        fun of(route: String, body: Any, purpose: String, data: Map<String, String> = emptyMap()): JournalEntry {
            val json = ApiClient.GSON.toJsonTree(body).asJsonObject
            require(json.has(KEY)) { "A journal entry's body needs an $KEY" }
            return JournalEntry(json, purpose, data, path = route)
        }
    }
}

package fr.bde_teknantes.lnb.economy.ledger

import fr.bde_teknantes.lnb.economy.api.TransferBody

/**
 * A transfer the server decided to make and the site hasn't confirmed or refused yet.
 *
 * [purpose] names the [TransferJournal.Handler] that finishes the job (gives chips, tells the players...), and [data]
 * holds what it needs, so the job can finish after a restart.
 */
data class JournalEntry(
    val body: TransferBody,
    val purpose: String,
    val data: Map<String, String> = emptyMap(),
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val createdAt: Long = 0,
    val lastError: String? = null,
) {
    val key: String get() = body.idempotencyKey
}

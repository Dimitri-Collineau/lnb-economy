package fr.bde_teknantes.lnb.economy.casino

/**
 * What the server knows about each casino's account, without Minecraft: its last known balance, and what part of it
 * is spoken for.
 *
 * Free balance = balance - chips in circulation - reservations. Chips in circulation must stay backed, and a
 * reservation keeps back the most a round in progress can still pay out.
 */
class CasinoBook(private val clock: () -> Long) {
    data class Account(val balance: Long, val frozen: Boolean, val fetchedAt: Long, val casino: Boolean = true)

    sealed interface Status {
        data class Open(val free: Long) : Status
        data object Frozen : Status
        data object Missing : Status

        /** The account exists but isn't of type casino: it has no casino licence. */
        data object NotCasino : Status

        /** Never read from the site, or read too long ago while the site is down. */
        data object Unknown : Status
    }

    private data class Reservation(val handle: String, val amount: Long, val expiresAt: Long)

    private val accounts = HashMap<String, Account>()
    private val missing = HashSet<String>()
    private val reservations = HashMap<Any, Reservation>()

    val handles: Set<String> get() = accounts.keys + missing

    fun account(handle: String): Account? = accounts[handle]

    fun update(handle: String, balance: Long, frozen: Boolean, casino: Boolean = true) {
        missing.remove(handle)
        accounts[handle] = Account(balance, frozen, clock(), casino)
    }

    /** A transfer's answer gives the balance right after it; it doesn't say whether the account is frozen. */
    fun updateBalance(handle: String, balance: Long) {
        val current = accounts[handle] ?: return
        accounts[handle] = current.copy(balance = balance, fetchedAt = clock())
    }

    /** The site says there is no such shared account (closed, or never existed). */
    fun markMissing(handle: String) {
        accounts.remove(handle)
        missing.add(handle)
    }

    /**
     * Keeps back [amount] of [handle] for the round known as [key] (one per machine), replacing what [key] kept back
     * before. Forgotten after [ttlMs], in case the round never ends (a table broken mid-round).
     */
    fun reserve(key: Any, handle: String, amount: Long, ttlMs: Long) {
        reservations[key] = Reservation(handle, amount, clock() + ttlMs)
    }

    fun release(key: Any) {
        reservations.remove(key)
    }

    fun reserved(handle: String, except: Any? = null): Long {
        val now = clock()
        reservations.values.removeIf { it.expiresAt <= now }
        return reservations.filter { (key, value) -> value.handle == handle && key != except }.values.sumOf { it.amount }
    }

    /**
     * Whether [handle] can be used, and its free balance. [outstanding] is the value of its chips in circulation.
     * [except] leaves out that key's own reservation, for a round checking itself again.
     */
    fun status(handle: String, outstanding: Long, maxAgeMs: Long, except: Any? = null): Status {
        if (handle in missing) return Status.Missing
        val account = accounts[handle] ?: return Status.Unknown
        if (clock() - account.fetchedAt > maxAgeMs) return Status.Unknown
        if (!account.casino) return Status.NotCasino
        if (account.frozen) return Status.Frozen
        return Status.Open(account.balance - outstanding - reserved(handle, except))
    }
}

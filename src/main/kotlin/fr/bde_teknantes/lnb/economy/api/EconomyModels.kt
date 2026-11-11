package fr.bde_teknantes.lnb.economy.api

/*
 * The bodies and answers of the `/api/economy/server` routes, as in `shared/schemas/economy.ts` on the site.
 * Decoded with Gson: optional fields are nullable, and null fields are left out of request bodies.
 */

/** One of a player's accounts, as `GET /players/:uuid` lists it. */
data class AccountView(
    val id: String,
    val kind: String,
    /** Shared accounts only, without the `#`. */
    val handle: String?,
    /** Shared accounts only. */
    val name: String?,
    val balance: Long,
    /** `owner`, `member` or `viewer`; always `owner` for the personal account. */
    val role: String,
    val frozen: Boolean,
    val memberCount: Int,
) {
    val isPersonal: Boolean get() = kind == KIND_PERSONAL

    /** Owners and members spend from an account; viewers only read it. Frozen accounts give nothing. */
    val canSpend: Boolean get() = !frozen && (role == ROLE_OWNER || role == ROLE_MEMBER)

    companion object {
        const val KIND_PERSONAL = "personal"
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
        const val ROLE_VIEWER = "viewer"
    }
}

/** `GET /players/:uuid`: the player's accounts, personal first, and the one that pays by default. */
data class ServerPlayer(
    val userId: String,
    val defaultAccountId: String,
    val accounts: List<AccountView>,
) {
    val defaultAccount: AccountView? get() = accounts.firstOrNull { it.id == defaultAccountId }
}

/** `GET /accounts/:handle`: a shared account. The handle has no `#`. */
data class ServerAccount(
    val id: String,
    val handle: String,
    val name: String,
    val balance: Long,
    val frozen: Boolean,
)

/** `PUT /players/:uuid/default-account`. */
data class DefaultAccountBody(val account: String)

/** One side of a transfer: a player (with or without an account), or an account alone. */
data class Party(val player: String? = null, val account: String? = null)

/** `POST /transfers`. */
data class TransferBody(
    val idempotencyKey: String,
    val from: Party,
    val to: Party,
    val amount: Long,
    val kind: String,
    val memo: String? = null,
) {
    companion object {
        const val KIND_PAYMENT = "payment"
        const val KIND_CASINO = "casino"
        const val MAX_MEMO_LENGTH = 140
        const val MAX_AMOUNT = 1_000_000_000_000L
    }
}

data class TransferSide(val accountId: String, val handle: String?, val balance: Long)

data class TransferResult(
    val transactionId: Long,
    /** True when the key had already been used: nothing moved again. */
    val replayed: Boolean,
    val amount: Long,
    val from: TransferSide,
    val to: TransferSide,
)

/** A shop's item: its ID, the stack as the game saved it (SNBT, never read by the site), and its name in the game. */
data class ShopItem(val id: String, val snbt: String, val name: String)

data class ShopPosition(val world: String, val x: Int, val y: Int, val z: Int)

/**
 * `POST /shops` and `PUT /shops/:id`. [buyPrice] null means the shop doesn't buy back: Gson leaves it out, which is
 * what the site expects.
 */
data class ShopSettingsBody(
    val player: String,
    val account: String,
    val item: ShopItem,
    val lotSize: Int,
    val sellPrice: Long,
    val buyPrice: Long?,
    val position: ShopPosition,
)

data class ShopAccountView(val id: String, val kind: String, val handle: String?)

data class ShopItemView(val id: String, val name: String)

/** A shop as the site has it. */
data class ServerShop(
    val id: String,
    val account: ShopAccountView,
    val item: ShopItemView,
    val lotSize: Int,
    val sellPrice: Long,
    val buyPrice: Long?,
    val position: ShopPosition,
    val disabled: Boolean,
    /** `removed` (its block was broken) or `admin`. */
    val disabledReason: String?,
) {
    companion object {
        const val DISABLED_BY_ADMIN = "admin"
    }
}

/** `POST /shops/:id/disable`, sent through the journal so a broken shop always closes. */
data class ShopDisableBody(val idempotencyKey: String, val player: String?)

/**
 * `POST /shops/:id/orders`. [account] pays for a `buy` (default account when null) and is paid for a `sell`
 * (personal account when null).
 */
data class ShopOrderBody(
    val idempotencyKey: String,
    val player: String,
    val account: String?,
    val direction: String,
    val lots: Int,
    val expectedLotPrice: Long,
) {
    companion object {
        const val BUY = "buy"
        const val SELL = "sell"
    }
}

/** One player in a trade, and the account they trade with (their default one when null). */
data class TradeSide(val player: String, val account: String?)

/** `POST /trades`: what [from] gave minus what [to] gave, from the account [from] trades with to the other's. */
data class TradeBody(
    val idempotencyKey: String,
    val from: TradeSide,
    val to: TradeSide,
    val amount: Long,
    val memo: String? = null,
)

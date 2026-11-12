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
    /** Shared accounts only: one of [AccountTypes]. Null on a site that has no types yet. */
    val type: String? = null,
) {
    val isPersonal: Boolean get() = kind == KIND_PERSONAL

    /** Whether machines and counters can be linked to it. A site without types lets every shared account be one. */
    val isCasino: Boolean get() = !isPersonal && (type == null || type == AccountTypes.CASINO)

    /** Owners and members spend from an account; viewers only read it. Frozen accounts give nothing. */
    val canSpend: Boolean get() = !frozen && (role == ROLE_OWNER || role == ROLE_MEMBER)

    companion object {
        const val KIND_PERSONAL = "personal"
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
        const val ROLE_VIEWER = "viewer"
    }
}

/** What a shared account is for. Only `casino` changes anything in the game, and it needs a casino licence. */
object AccountTypes {
    const val TEAM = "team"
    const val SHOP = "shop"
    const val CASINO = "casino"
    val ALL = listOf(TEAM, SHOP, CASINO)
}

/** A player's casino licences: [free] of the [total] aren't bound to a casino yet. */
data class Licenses(val total: Int, val free: Int)

/** `GET /players/:uuid`: the player's accounts, personal first, and the one that pays by default. */
data class ServerPlayer(
    val userId: String,
    val defaultAccountId: String,
    val accounts: List<AccountView>,
    val casinoLicenses: Licenses? = null,
    val casinoLicensePrice: Long? = null,
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
    val type: String? = null,
) {
    /** See [AccountView.isCasino]. */
    val isCasino: Boolean get() = type == null || type == AccountTypes.CASINO
}

/** `POST /accounts`: [player] opens a shared account and owns it. */
data class AccountCreateBody(val player: String, val handle: String, val name: String, val type: String?)

/** `PATCH /accounts/:handle`: an owner renames it, changes its type, or both. Null fields are left as they are. */
data class AccountPatchBody(val player: String, val name: String?, val type: String?)

/** A member of a shared account, by their Minecraft username. */
data class MemberView(val userId: String, val username: String?, val role: String)

data class PendingInvitation(val id: String, val username: String, val createdAt: String?)

/** `GET /accounts/:handle/members`. [invitations] is empty for anyone but an owner. */
data class AccountMembers(val account: AccountView, val members: List<MemberView>, val invitations: List<PendingInvitation>)

/** `POST /accounts/:handle/invitations`. */
data class InviteBody(val player: String, val username: String)

data class InvitedAccount(val id: String, val handle: String, val name: String, val type: String?)

/** The answer to an invitation sent: [inviteeUuids] are every Minecraft account of the invited person. */
data class InviteResult(val id: String, val username: String, val account: InvitedAccount, val invitedBy: String?, val inviteeUuids: List<String>?)

/** An invitation waiting for a player's answer. */
data class InvitationView(val id: String, val account: InvitedAccount, val invitedBy: String?, val createdAt: String?)

data class InvitationList(val items: List<InvitationView>)

/** A body that only names the acting player. */
data class PlayerBody(val player: String)

/** `POST /licenses`: [player] buys a casino licence, paid from [account] (the default account when null). */
data class LicenseBody(val idempotencyKey: String, val player: String, val account: String?)

/** [transactionId] is null when licences are free. */
data class LicenseResult(val transactionId: Long?, val replayed: Boolean, val amount: Long, val from: TransferSide?, val licenses: Licenses)

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

/** `POST /mints`: new money on [player]'s personal account. [reason] says where it comes from. */
data class MintBody(val idempotencyKey: String, val player: String, val amount: Long, val reason: String) {
    companion object {
        const val MOB_DROP = "mobDrop"
    }
}

data class MintResult(val transactionId: Long?, val replayed: Boolean, val amount: Long, val to: TransferSide)

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

/** An item stored on the site: its ID, its name in the game, and the stack as the game saved it. */
data class StoredItem(val id: String, val name: String, val snbt: String)

/** `POST /auctions`: one stack at a fixed price. [account] is paid on a sale (default account when null). */
data class ListingBody(
    val idempotencyKey: String,
    val player: String,
    val account: String?,
    val item: ShopItem,
    val count: Int,
    val price: Long,
) {
    companion object {
        const val MAX_PRICE = 1_000_000_000L
    }
}

/** A listing at the auction house. [status] is `active`, `sold`, `cancelled` or `expired`. */
data class ServerListing(
    val id: String,
    val item: StoredItem,
    val count: Int,
    val price: Long,
    val seller: String,
    val status: String,
    val createdAt: String,
    val expiresAt: String,
)

data class ListingPage(val items: List<ServerListing>, val nextCursor: String?)

/** `GET /players/:uuid/listings`: a player's listings up now, and the limits for new ones. */
data class PlayerListings(val items: List<ServerListing>, val max: Int, val taxPercent: Int, val days: Int)

/** `POST /auctions/:id/cancel` and `POST /mailbox/:id/claim`. */
data class ListingActionBody(val idempotencyKey: String, val player: String)

/** `POST /auctions/:id/buy`. [account] pays (default account when null). */
data class ListingBuyBody(val idempotencyKey: String, val player: String, val account: String?)

/** An item waiting for a player on the site. [reason]: `bought`, `cancelled`, `expired` or `removed`. */
data class MailboxView(val id: String, val item: StoredItem, val count: Int, val reason: String, val createdAt: String)

data class SiteMailbox(val items: List<MailboxView>)

/** The answer of a purchase: a transfer's (from the buyer, to the seller), the tax, and the item to claim. */
data class ListingBuyResult(val tax: Long, val listingId: String, val mailboxItem: MailboxView, val sellerUserId: String? = null)

/** A listing of the player that sold while nobody told them. */
data class SaleView(val listingId: String, val item: ShopItemView, val count: Int, val price: Long, val tax: Long, val buyer: String?, val soldAt: String?)

data class SaleList(val items: List<SaleView>)

/** `POST /players/:uuid/sales/seen`. */
data class SalesSeenBody(val listingIds: List<String>)

package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** What the client knows about the player's economy. */
enum class EconomyStatus {
    /** Not asked yet, or waiting for the site. */
    LOADING,
    READY,

    /** No LNB account linked to this Minecraft account (a guest). */
    NOT_LINKED,

    /** Linked, but the LNB account has no access to the economy. */
    NOT_ELIGIBLE,

    /** The site can't be reached or the economy is switched off. */
    UNAVAILABLE,
}

/** One account as the client shows it. [handle] and [name] are null for the personal account. */
data class AccountEntry(
    val id: String,
    val handle: String?,
    val name: String?,
    val balance: Long,
    val role: String,
    val frozen: Boolean,
    val canSpend: Boolean,
) {
    val isPersonal: Boolean get() = handle == null

    companion object {
        fun write(buf: FriendlyByteBuf, entry: AccountEntry) {
            buf.writeUtf(entry.id, 64)
            buf.writeNullable(entry.handle) { b, v -> b.writeUtf(v, 32) }
            buf.writeNullable(entry.name) { b, v -> b.writeUtf(v, 128) }
            buf.writeVarLong(entry.balance)
            buf.writeUtf(entry.role, 16)
            buf.writeBoolean(entry.frozen)
            buf.writeBoolean(entry.canSpend)
        }

        fun read(buf: FriendlyByteBuf) = AccountEntry(
            id = buf.readUtf(64),
            handle = buf.readNullable { it.readUtf(32) },
            name = buf.readNullable { it.readUtf(128) },
            balance = buf.readVarLong(),
            role = buf.readUtf(16),
            frozen = buf.readBoolean(),
            canSpend = buf.readBoolean(),
        )
    }
}

/** Server -> client: the player's accounts (personal first) and the one that pays by default. */
data class AccountsSnapshotPayload(
    val status: EconomyStatus,
    val accounts: List<AccountEntry>,
    val defaultAccountId: String?,
) : CustomPacketPayload {
    override fun type() = TYPE

    val defaultAccount: AccountEntry? get() = accounts.firstOrNull { it.id == defaultAccountId }

    companion object {
        private const val MAX_ACCOUNTS = 256

        val TYPE = CustomPacketPayload.Type<AccountsSnapshotPayload>(LnbEconomy.id("accounts"))

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AccountsSnapshotPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeEnum(payload.status)
                buf.writeCollection(payload.accounts, AccountEntry::write)
                buf.writeNullable(payload.defaultAccountId) { b, v -> b.writeUtf(v, 64) }
            },
            { buf ->
                AccountsSnapshotPayload(
                    status = buf.readEnum(EconomyStatus::class.java),
                    accounts = buf.readCollection({ size -> ArrayList(minOf(size, MAX_ACCOUNTS)) }, AccountEntry::read),
                    defaultAccountId = buf.readNullable { it.readUtf(64) },
                )
            },
        )
    }
}

/**
 * Client -> server: send me my accounts, read again from the site. Rate-limited on the server.
 * A single instance: [StreamCodec.unit] only encodes the very instance it was given.
 */
object RefreshAccountsPayload : CustomPacketPayload {
    override fun type() = TYPE

    val TYPE = CustomPacketPayload.Type<RefreshAccountsPayload>(LnbEconomy.id("refresh_accounts"))
    val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RefreshAccountsPayload> = StreamCodec.unit(RefreshAccountsPayload)
}

/** Client -> server: make this account the one that pays by default. */
data class SetDefaultAccountPayload(val accountId: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SetDefaultAccountPayload>(LnbEconomy.id("set_default_account"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SetDefaultAccountPayload> = StreamCodec.of(
            { buf, payload -> buf.writeUtf(payload.accountId, 64) },
            { buf -> SetDefaultAccountPayload(buf.readUtf(64)) },
        )
    }
}

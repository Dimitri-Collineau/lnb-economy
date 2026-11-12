package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Client -> server: open a shared account called [name], known as #[handle], of [type]. */
data class AccountCreatePayload(val handle: String, val name: String, val type: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AccountCreatePayload>(LnbEconomy.id("account_create"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AccountCreatePayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.handle, 32)
                buf.writeUtf(p.name, 64)
                buf.writeUtf(p.type, 16)
            },
            { buf -> AccountCreatePayload(buf.readUtf(32), buf.readUtf(64), buf.readUtf(16)) },
        )
    }
}

/** Client -> server: rename #[handle], change its type, or both (null leaves it as it is). Owners only. */
data class AccountEditPayload(val handle: String, val name: String?, val type: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AccountEditPayload>(LnbEconomy.id("account_edit"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AccountEditPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.handle, 32)
                buf.writeNullable(p.name) { b, v -> b.writeUtf(v, 64) }
                buf.writeNullable(p.type) { b, v -> b.writeUtf(v, 16) }
            },
            { buf -> AccountEditPayload(buf.readUtf(32), buf.readNullable { it.readUtf(64) }, buf.readNullable { it.readUtf(16) }) },
        )
    }
}

/** Client -> server: send me the members of #[handle]. */
data class AccountMembersRequestPayload(val handle: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AccountMembersRequestPayload>(LnbEconomy.id("account_members_request"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AccountMembersRequestPayload> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.handle, 32) },
            { buf -> AccountMembersRequestPayload(buf.readUtf(32)) },
        )
    }
}

data class MemberEntry(val username: String, val role: String)

data class PendingEntry(val id: String, val username: String)

/** Server -> client: the members of #[handle], and for its owners the invitations not answered yet. */
data class AccountMembersPayload(val handle: String, val members: List<MemberEntry>, val pending: List<PendingEntry>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        private const val MAX = 256

        val TYPE = CustomPacketPayload.Type<AccountMembersPayload>(LnbEconomy.id("account_members"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AccountMembersPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.handle, 32)
                buf.writeCollection(p.members) { b, m ->
                    b.writeUtf(m.username, 64)
                    b.writeUtf(m.role, 16)
                }
                buf.writeCollection(p.pending) { b, i ->
                    b.writeUtf(i.id, 64)
                    b.writeUtf(i.username, 64)
                }
            },
            { buf ->
                AccountMembersPayload(
                    buf.readUtf(32),
                    buf.readCollection({ ArrayList(minOf(it, MAX)) }) { b -> MemberEntry(b.readUtf(64), b.readUtf(16)) },
                    buf.readCollection({ ArrayList(minOf(it, MAX)) }) { b -> PendingEntry(b.readUtf(64), b.readUtf(64)) },
                )
            },
        )
    }
}

/** Client -> server: invite the Minecraft account [username] to #[handle]. Owners only. */
data class AccountInvitePayload(val handle: String, val username: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AccountInvitePayload>(LnbEconomy.id("account_invite"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AccountInvitePayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.handle, 32)
                buf.writeUtf(p.username, 16)
            },
            { buf -> AccountInvitePayload(buf.readUtf(32), buf.readUtf(16)) },
        )
    }
}

/** Client -> server: take back the invitation [invitationId] to #[handle]. Owners only. */
data class InvitationCancelPayload(val handle: String, val invitationId: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<InvitationCancelPayload>(LnbEconomy.id("invitation_cancel"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, InvitationCancelPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.handle, 32)
                buf.writeUtf(p.invitationId, 64)
            },
            { buf -> InvitationCancelPayload(buf.readUtf(32), buf.readUtf(64)) },
        )
    }
}

/** Client -> server: accept or decline the invitation [invitationId]. */
data class InvitationAnswerPayload(val invitationId: String, val accept: Boolean) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<InvitationAnswerPayload>(LnbEconomy.id("invitation_answer"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, InvitationAnswerPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.invitationId, 64)
                buf.writeBoolean(p.accept)
            },
            { buf -> InvitationAnswerPayload(buf.readUtf(64), buf.readBoolean()) },
        )
    }
}

/**
 * Server -> client: how a request made from an account screen ended. The message is a translation [key] with [args],
 * built on the client in its own language.
 */
data class AccountFeedbackPayload(val ok: Boolean, val key: String, val args: List<String>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AccountFeedbackPayload>(LnbEconomy.id("account_feedback"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AccountFeedbackPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBoolean(p.ok)
                buf.writeUtf(p.key, 128)
                buf.writeCollection(p.args) { b, v -> b.writeUtf(v, 128) }
            },
            { buf -> AccountFeedbackPayload(buf.readBoolean(), buf.readUtf(128), buf.readCollection({ ArrayList(minOf(it, 8)) }) { b -> b.readUtf(128) }) },
        )
    }
}

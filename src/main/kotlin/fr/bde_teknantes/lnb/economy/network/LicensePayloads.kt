package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Server -> client: the player talked to the licence dealer called [dealer]. The price and licences come with the accounts. */
data class OpenLicensePayload(val dealer: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OpenLicensePayload>(LnbEconomy.id("open_license"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OpenLicensePayload> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.dealer, 64) },
            { buf -> OpenLicensePayload(buf.readUtf(64)) },
        )
    }
}

/** Client -> server: buy one casino licence, paid from [accountId] (null: the default account). */
data class LicenseBuyPayload(val accountId: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<LicenseBuyPayload>(LnbEconomy.id("license_buy"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, LicenseBuyPayload> = StreamCodec.of(
            { buf, p -> buf.writeNullable(p.accountId) { b, v -> b.writeUtf(v, 64) } },
            { buf -> LicenseBuyPayload(buf.readNullable { it.readUtf(64) }) },
        )
    }
}

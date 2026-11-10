package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Requests from players. The client is never trusted: everything is checked again here. Runs on the server thread. */
object ServerPayloadHandler {
    fun handleRefresh(payload: RefreshAccountsPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        PlayerAccounts.refresh(player.server, player.uuid, force = false)
    }

    fun handleSetDefault(payload: SetDefaultAccountPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val server = player.server
        val account = PlayerAccounts.accounts(player.uuid)?.accounts?.firstOrNull { it.id == payload.accountId }
        if (account == null || !account.canSpend) {
            player.sendSystemMessage(Component.translatable("economy.lnbeconomy.default.cant_spend").withStyle(ChatFormatting.RED))
            return
        }
        EconomyApi.setDefaultAccount(player.uuid, account.id).thenAccept { result ->
            server.execute {
                when (result) {
                    is ApiResult.Ok -> {
                        PlayerAccounts.update(server, player.uuid, result.body)
                        player.sendSystemMessage(Component.translatable("economy.lnbeconomy.default.set", Texts.account(account.handle)))
                    }
                    else -> player.sendSystemMessage(Texts.problem(result).copy().withStyle(ChatFormatting.RED))
                }
            }
        }
    }
}

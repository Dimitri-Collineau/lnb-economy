package fr.bde_teknantes.lnb.economy.casino

import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.account.RealPlayers
import fr.bde_teknantes.lnb.economy.api.LicenseBody
import fr.bde_teknantes.lnb.economy.api.LicenseResult
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.mail.ItemMailbox
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.economy.network.LicenseBuyPayload
import fr.bde_teknantes.lnb.economy.network.OpenLicensePayload
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Casino licences, sold by the dealer. A licence lets its holder make one shared account a casino; the site keeps
 * count, burns the price and has the final say. The book handed over is a keepsake: it opens no door by itself.
 */
object Licenses {
    private const val PURPOSE = "casinoLicense"
    private const val ROUTE = "/licenses"

    /** How long after talking to a dealer a purchase is accepted. */
    private const val SESSION_MS = 5 * 60_000L

    private object Data {
        const val PLAYER = "player"
        const val DEALER = "dealer"
    }

    /** Players who talked to a dealer, when, and the dealer's name for the book: purchases only come from there. */
    private val talking = HashMap<UUID, Pair<Long, String>>()

    fun register() {
        TransferJournal.register(PURPOSE, ::finish)
    }

    fun open(player: ServerPlayer, dealer: CasinoDealer) {
        if (!RealPlayers.isReal(player)) return
        talking[player.uuid] = System.currentTimeMillis() to dealer.name.string
        PlayerAccounts.refresh(player.server, player.uuid, force = false)
        PacketDistributor.sendToPlayer(player, OpenLicensePayload(dealer.name.string.take(64)))
    }

    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        talking.remove(event.entity.uuid)
    }

    fun handleBuy(payload: LicenseBuyPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val (since, dealer) = talking[player.uuid] ?: return
        if (System.currentTimeMillis() - since > SESSION_MS) return
        PlayerAccounts.noEconomyMessage(player.uuid)?.let { return error(player, it) }
        val entry = PlayerAccounts.get(player.uuid)
        val accounts = entry?.player
        if (entry?.status != EconomyStatus.READY || accounts == null) return error(player, Component.translatable("economy.lnbeconomy.problem.unavailable"))
        if (accounts.casinoLicensePrice == null) return error(player, Component.translatable("economy.lnbeconomy.license.not_sold"))
        if (payload.accountId != null && accounts.accounts.none { it.id == payload.accountId && it.canSpend }) {
            return error(player, Component.translatable("economy.lnbeconomy.problem.notAllowed"))
        }
        // One at a time: a second click while the site answers would buy two.
        if (TransferJournal.pending.any { it.purpose == PURPOSE && it.data[Data.PLAYER] == player.uuid.toString() }) return
        TransferJournal.submit(ROUTE, PURPOSE, mapOf(Data.PLAYER to player.uuid.toString(), Data.DEALER to dealer)) { key ->
            LicenseBody(key, player.uuid.toString(), payload.accountId)
        }
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue(Data.PLAYER))
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> player?.sendSystemMessage(Component.translatable("economy.lnbeconomy.license.delayed").withStyle(ChatFormatting.GRAY))
            is Outcome.Refused -> player?.let { error(it, Component.translatable("economy.lnbeconomy.license.refused", Texts.problem(outcome.result))) }
            is Outcome.Done -> {
                val result = outcome.decode<LicenseResult>()
                result.from?.let { PlayerAccounts.applyBalance(server, it.accountId, it.balance) }
                val name = player?.gameProfile?.name ?: server.profileCache?.get(uuid)?.orElse(null)?.name ?: "?"
                ItemMailbox.deliver(server, uuid, listOf(book(entry.data[Data.DEALER].orEmpty(), name, result.licenses.total)))
                if (player != null) {
                    PlayerAccounts.refresh(server, uuid)
                    player.sendSystemMessage(
                        Component.translatable("economy.lnbeconomy.license.bought", Texts.amount(result.amount, player), result.licenses.free).withStyle(ChatFormatting.GREEN),
                    )
                    player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1f)
                }
            }
        }
    }

    /** The keepsake: a signed book from the dealer, naming the holder and which of their licences it is. */
    private fun book(dealer: String, holder: String, number: Int): ItemStack {
        val pages = listOf(
            Component.translatable("item.lnbeconomy.license.page1", holder, number),
            Component.translatable("item.lnbeconomy.license.page2"),
        ).map { Filterable.passThrough<Component>(it) }
        val author = dealer.ifBlank { "LNB" }.take(16)
        return ItemStack(Items.WRITTEN_BOOK).apply {
            set(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent(Filterable.passThrough("Casino Licence"), author, 0, pages, true))
            set(DataComponents.ITEM_NAME, Component.translatable("item.lnbeconomy.license"))
        }
    }

    private fun error(player: ServerPlayer, message: Component) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED))
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.5f)
    }
}

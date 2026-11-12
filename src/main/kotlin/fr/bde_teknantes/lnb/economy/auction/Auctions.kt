package fr.bde_teknantes.lnb.economy.auction

import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.account.RealPlayers
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.ListingActionBody
import fr.bde_teknantes.lnb.economy.api.ListingBody
import fr.bde_teknantes.lnb.economy.api.ListingBuyBody
import fr.bde_teknantes.lnb.economy.api.ListingBuyResult
import fr.bde_teknantes.lnb.economy.api.MailboxView
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.api.ServerListing
import fr.bde_teknantes.lnb.economy.api.ShopItem
import fr.bde_teknantes.lnb.economy.item.Escrow
import fr.bde_teknantes.lnb.economy.item.ItemStacks
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.mail.ItemMailbox
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.AuctionBuyPayload
import fr.bde_teknantes.lnb.economy.network.AuctionCancelPayload
import fr.bde_teknantes.lnb.economy.network.AuctionCollectPayload
import fr.bde_teknantes.lnb.economy.network.AuctionEntry
import fr.bde_teknantes.lnb.economy.network.AuctionListingsPayload
import fr.bde_teknantes.lnb.economy.network.AuctionMailboxPayload
import fr.bde_teknantes.lnb.economy.network.AuctionRequestPayload
import fr.bde_teknantes.lnb.economy.network.AuctionSellPayload
import fr.bde_teknantes.lnb.economy.network.OpenAuctionPayload
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * The auction house in the game. Listings, purchases and the mailbox live on the site; the server shows them and
 * moves items in and out of the game. A listed item leaves the player's hand into the journal entry, and comes back
 * if the site refuses. A bought item, or one taken back, lands in the player's mailbox on the site; the server claims
 * it with a key and hands it over through the local mailbox, so a crash never loses or doubles it.
 */
object Auctions {
    private const val SELL = "auctionSell"
    private const val BUY = "auctionBuy"
    private const val CANCEL = "auctionCancel"
    private const val CLAIM = "auctionClaim"

    /** How long an opened auction house accepts orders without being opened again. */
    private const val SESSION_MS = 30 * 60_000L

    private object Data {
        const val PLAYER = "player"
        const val ITEMS = "items"
        const val ITEM = "item"
    }

    /** Players with the auction house open, and since when: orders only come from an opened auction house. */
    private val opened = HashMap<UUID, Long>()

    fun register() {
        TransferJournal.register(SELL, ::finishSell)
        TransferJournal.register(BUY, ::finishBuy)
        TransferJournal.register(CANCEL, ::finishCancel)
        TransferJournal.register(CLAIM, ::finishClaim)
    }

    fun registerCommands(event: RegisterCommandsEvent) {
        for (name in listOf("hdv", "ah")) {
            event.dispatcher.register(Commands.literal(name).executes { context ->
                val player = context.source.playerOrException
                if (!EconomyConfig.AUCTIONS_COMMAND.get()) {
                    player.sendSystemMessage(Component.translatable("economy.lnbeconomy.auction.command_off"))
                    0
                } else if (open(player)) 1 else 0
            })
        }
    }

    /** Opens the auction house: from the command, a block or the auctioneer. */
    fun open(player: ServerPlayer): Boolean {
        if (!RealPlayers.isReal(player)) return false
        if (!EconomyConfig.AUCTIONS.get()) {
            player.sendSystemMessage(Component.translatable("economy.lnbeconomy.auction.switched_off"))
            return false
        }
        opened[player.uuid] = System.currentTimeMillis()
        PlayerAccounts.refresh(player.server, player.uuid, force = false)
        PacketDistributor.sendToPlayer(player, OpenAuctionPayload)
        return true
    }

    private fun isOpen(player: ServerPlayer): Boolean {
        val since = opened[player.uuid] ?: return false
        return EconomyConfig.AUCTIONS.get() && System.currentTimeMillis() - since < SESSION_MS
    }

    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        opened.remove(event.entity.uuid)
    }

    fun handleRequest(payload: AuctionRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!isOpen(player)) return
        val server = player.server
        when (payload.tab) {
            AuctionRequestPayload.BROWSE -> EconomyApi.listings(payload.query, payload.sort, payload.cursor).thenAccept { result ->
                server.execute {
                    when (result) {
                        is ApiResult.Ok -> send(player, AuctionListingsPayload(AuctionRequestPayload.BROWSE, entries(server, result.body.items), payload.cursor, result.body.nextCursor, 0, 0, 0))
                        else -> refused(player, result)
                    }
                }
            }
            AuctionRequestPayload.MINE -> EconomyApi.playerListings(player.uuid).thenAccept { result ->
                server.execute {
                    when (result) {
                        is ApiResult.Ok -> send(
                            player,
                            AuctionListingsPayload(AuctionRequestPayload.MINE, entries(server, result.body.items), null, null, result.body.max, result.body.taxPercent, result.body.days),
                        )
                        else -> refused(player, result)
                    }
                }
            }
            AuctionRequestPayload.MAILBOX -> EconomyApi.siteMailbox(player.uuid).thenAccept { result ->
                server.execute {
                    when (result) {
                        is ApiResult.Ok -> send(player, AuctionMailboxPayload(result.body.items.map { mailEntry(server, it) }))
                        else -> refused(player, result)
                    }
                }
            }
        }
    }

    /** Lists [AuctionSellPayload.count] items of the stack in the player's hand. */
    fun handleSell(payload: AuctionSellPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!isOpen(player)) return
        // Any stack of the main inventory or the hotbar, not only the one in hand.
        if (payload.slot !in 0 until Inventory.INVENTORY_SIZE) return
        val stack = player.inventory.getItem(payload.slot)
        if (stack.isEmpty || payload.count !in 1..stack.count || payload.price !in 1..ListingBody.MAX_PRICE) return
        PlayerAccounts.noEconomyMessage(player.uuid)?.let {
            player.sendSystemMessage(it)
            return
        }
        if (payload.accountId != null && PlayerAccounts.accounts(player.uuid)?.accounts?.any { it.id == payload.accountId && it.canSpend } != true) {
            player.sendSystemMessage(Component.translatable("economy.lnbeconomy.problem.notAllowed"))
            return
        }
        val server = player.server
        val taken = stack.split(payload.count)
        player.inventory.setChanged()
        val tag = ItemStacks.toTag(taken, server.registryAccess()) ?: return
        val item = ShopItem(
            id = BuiltInRegistries.ITEM.getKey(taken.item).toString(),
            snbt = tag.toString(),
            name = taken.hoverName.string.take(64).ifBlank { BuiltInRegistries.ITEM.getKey(taken.item).path },
        )
        val data = mapOf(Data.PLAYER to player.uuid.toString(), Data.ITEMS to Escrow.hold(listOf(taken), server.registryAccess()))
        TransferJournal.submit("/auctions", SELL, data) { key ->
            ListingBody(key, player.uuid.toString(), payload.accountId, item, payload.count, payload.price)
        }
    }

    fun handleBuy(payload: AuctionBuyPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!isOpen(player) || !isId(payload.listingId)) return
        PlayerAccounts.noEconomyMessage(player.uuid)?.let {
            player.sendSystemMessage(it)
            return
        }
        TransferJournal.submit("/auctions/${payload.listingId}/buy", BUY, mapOf(Data.PLAYER to player.uuid.toString())) { key ->
            ListingBuyBody(key, player.uuid.toString(), payload.accountId)
        }
    }

    fun handleCancel(payload: AuctionCancelPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        // Taking a listing back works while the auction house is switched off, so nothing stays stuck.
        if (opened[player.uuid] == null || !isId(payload.listingId)) return
        TransferJournal.submit("/auctions/${payload.listingId}/cancel", CANCEL, mapOf(Data.PLAYER to player.uuid.toString())) { key ->
            ListingActionBody(key, player.uuid.toString())
        }
    }

    fun handleCollect(payload: AuctionCollectPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (opened[player.uuid] == null) return
        collectAll(player.server, player.uuid)
    }

    /** Claims every item waiting for the player on the site. */
    private fun collectAll(server: MinecraftServer, player: UUID) {
        EconomyApi.siteMailbox(player).thenAccept { result ->
            if (result !is ApiResult.Ok) return@thenAccept
            server.execute { result.body.items.forEach { claim(player, it.id) } }
        }
    }

    /** Claims one item, unless a claim for it is already waiting in the journal. */
    private fun claim(player: UUID, itemId: String) {
        if (!isId(itemId)) return
        val route = "/mailbox/$itemId/claim"
        if (TransferJournal.pending.any { it.route == route }) return
        TransferJournal.submit(route, CLAIM, mapOf(Data.PLAYER to player.toString(), Data.ITEM to itemId)) { key ->
            ListingActionBody(key, player.toString())
        }
    }

    private fun finishSell(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue(Data.PLAYER))
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> player?.let { delayed(it) }
            is Outcome.Refused -> {
                Escrow.release(server, entry, Data.ITEMS, uuid)
                player?.let { refused(it, outcome.result) }
            }
            is Outcome.Done -> {
                val listing = outcome.decode<ServerListing>()
                player?.sendSystemMessage(
                    Component.translatable("economy.lnbeconomy.auction.listed", listing.count, listing.item.name, Texts.amount(listing.price, player)).withStyle(ChatFormatting.GREEN),
                )
            }
        }
    }

    private fun finishBuy(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue(Data.PLAYER))
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> player?.let { delayed(it) }
            is Outcome.Refused -> player?.let { refused(it, outcome.result) }
            is Outcome.Done -> {
                val result = outcome.result
                val bought = outcome.decode<ListingBuyResult>()
                PlayerAccounts.applyTransfer(server, result)
                player?.sendSystemMessage(
                    Component.translatable("economy.lnbeconomy.auction.bought", bought.mailboxItem.count, bought.mailboxItem.item.name, Texts.amount(result.amount, player))
                        .withStyle(ChatFormatting.GREEN),
                )
                claim(uuid, bought.mailboxItem.id)
            }
        }
    }

    private fun finishCancel(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue(Data.PLAYER))
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> player?.let { delayed(it) }
            is Outcome.Refused -> player?.let { refused(it, outcome.result) }
            is Outcome.Done -> {
                player?.sendSystemMessage(Component.translatable("economy.lnbeconomy.auction.cancelled").withStyle(ChatFormatting.GREEN))
                collectAll(server, uuid)
            }
        }
    }

    private fun finishClaim(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue(Data.PLAYER))
        when (outcome) {
            is Outcome.Delayed -> Unit
            is Outcome.Refused -> {
                // Already claimed with another key: that claim handed it over.
                if ((outcome.result as? ApiResult.Http)?.problem != Problems.ALREADY_CLAIMED) {
                    LnbEconomy.LOGGER.warn("Claiming mailbox item {} for {} was refused: {}", entry.data[Data.ITEM], uuid, outcome.result.describe())
                }
            }
            is Outcome.Done -> {
                val item = outcome.decode<MailboxView>()
                val tag = runCatching { TagParser.parseTag(item.item.snbt) }.getOrNull()
                if (tag == null) {
                    LnbEconomy.LOGGER.error("Mailbox item {} for {} has an unreadable SNBT, kept on the site's record only: {}", item.id, uuid, item.item.snbt)
                    return
                }
                ItemMailbox.deliverSaved(server, uuid, listOf(tag))
            }
        }
    }

    private fun entries(server: MinecraftServer, listings: List<ServerListing>): List<AuctionEntry> =
        listings.map { AuctionEntry(it.id, stackOf(server, it.item.snbt), it.item.name, it.count, it.price, it.seller, it.expiresAt) }

    private fun mailEntry(server: MinecraftServer, item: MailboxView): AuctionEntry =
        AuctionEntry(item.id, stackOf(server, item.item.snbt), item.item.name, item.count, 0, item.reason, item.createdAt)

    /** The stack to show for an item stored on the site, or nothing when the game can't read it any more. */
    private fun stackOf(server: MinecraftServer, snbt: String): ItemStack {
        val tag = runCatching { TagParser.parseTag(snbt) }.getOrNull() as? CompoundTag ?: return ItemStack.EMPTY
        return ItemStacks.fromTag(tag, server.registryAccess()) ?: ItemStack.EMPTY
    }

    private fun isId(id: String): Boolean = runCatching { UUID.fromString(id) }.isSuccess

    private fun send(player: ServerPlayer, payload: net.minecraft.network.protocol.common.custom.CustomPacketPayload) =
        PacketDistributor.sendToPlayer(player, payload)

    private fun delayed(player: ServerPlayer) =
        player.sendSystemMessage(Component.translatable("economy.lnbeconomy.auction.delayed").withStyle(ChatFormatting.GRAY))

    private fun refused(player: ServerPlayer, result: ApiResult<*>) =
        player.sendSystemMessage(Component.translatable("economy.lnbeconomy.auction.refused", Texts.problem(result)).withStyle(ChatFormatting.RED))
}

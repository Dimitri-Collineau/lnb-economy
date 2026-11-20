package fr.bde_teknantes.lnb.economy.market

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.auction.Auctions
import fr.bde_teknantes.lnb.economy.item.Escrow
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.RetryPolicy
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Selling to and buying from the Market, through the journal (so an order survives a crash and is sent again with its
 * key until the site answers). Sections 9 and 10 of the site's `market-mod.md`.
 *
 * A **sale** takes the items out of the inventory first and keeps them in the journal entry (escrow): they go back to
 * the player only on a definitive refusal, and are dropped for good only when the site confirms the sale. A
 * **purchase** moves nothing until the site answers; the item then waits in the site's mailbox, and is claimed at once.
 */
object MarketOrders {
    const val SELL = "marketSell"
    const val BUY = "marketBuy"

    private object Data {
        const val PLAYER = "player"
        const val ITEMS = "items"
        const val ITEM = "item"
        const val NAME = "name"
        const val QUANTITY = "quantity"
    }

    fun register() {
        TransferJournal.register(SELL, ::finishSell)
        TransferJournal.register(BUY, ::finishBuy)
    }

    /** Why an order wasn't even sent: the translation key and arguments. */
    class Cant(val key: String, vararg val args: Any)

    private fun itemOf(id: String): Item? = ResourceLocation.tryParse(id)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }

    /** What is wrong with an order for [quantity] of [itemId] before the site is asked, or null. */
    private fun problem(player: ServerPlayer, itemId: String, quantity: Int, selling: Boolean): Cant? {
        val market = Markets.state
        val settings = market.settings
        if (!market.isOpen || settings == null) return Cant("economy.lnbeconomy.market.cant.closed")
        if (!(if (selling) settings.sell else settings.buy)) return Cant(if (selling) "economy.lnbeconomy.market.cant.sell_off" else "economy.lnbeconomy.market.cant.buy_off")
        val item = market.item(itemId)
        if (item == null || itemOf(itemId) == null) return Cant("economy.lnbeconomy.market.cant.unknown_item")
        if (!(if (selling) item.sellable else item.buyable)) return Cant(if (selling) "economy.lnbeconomy.problem.notSellable" else "economy.lnbeconomy.problem.notBuyable")
        if (quantity < 1 || quantity > settings.maxOrderItems) return Cant("economy.lnbeconomy.market.cant.quantity", settings.maxOrderItems)
        PlayerAccounts.noEconomyMessage(player.uuid)?.let { return Cant("economy.lnbeconomy.market.cant.no_economy", it) }
        return null
    }

    /** The plain stacks of [item] in the player's main inventory and hotbar, as slots. */
    private fun plainSlots(player: ServerPlayer, item: Item): List<Int> =
        (0 until player.inventory.items.size).filter { slot ->
            val stack = player.inventory.items[slot]
            stack.`is`(item) && PlainItems.isPlain(stack)
        }

    /** How many plain [itemId] the player carries, for the screen. */
    fun carried(player: ServerPlayer, itemId: String): Int {
        val item = itemOf(itemId) ?: return 0
        return plainSlots(player, item).sumOf { player.inventory.items[it].count }
    }

    /** Sells [quantity] of [itemId] from the inventory for what the player saw ([expectedTotal], cents), onto [accountId] if the site allows it. */
    fun sell(player: ServerPlayer, itemId: String, quantity: Int, expectedTotal: Long, accountId: String? = null) {
        problem(player, itemId, quantity, selling = true)?.let { return cant(player, it) }
        val item = itemOf(itemId) ?: return
        val server = player.server
        val slots = plainSlots(player, item)
        if (slots.sumOf { player.inventory.items[it].count } < quantity) {
            return cant(player, Cant("economy.lnbeconomy.market.cant.not_enough", quantity, ItemStack(item).hoverName))
        }
        // Checked again at the moment of taking: nothing else runs on the server thread in between.
        if (accountId != null && PlayerAccounts.accounts(player.uuid)?.accounts?.any { it.id == accountId } != true) {
            return cant(player, Cant("economy.lnbeconomy.problem.notAllowed"))
        }
        val taken = ArrayList<ItemStack>()
        var left = quantity
        for (slot in slots) {
            if (left == 0) break
            val stack = player.inventory.items[slot]
            val part = stack.split(minOf(left, stack.count))
            taken += part
            left -= part.count
        }
        player.inventory.setChanged()
        val data = mapOf(
            Data.PLAYER to player.uuid.toString(),
            Data.ITEMS to Escrow.hold(taken, server.registryAccess()),
            Data.ITEM to itemId,
            Data.NAME to ItemStack(item).hoverName.string,
            Data.QUANTITY to quantity.toString(),
        )
        TransferJournal.submit(RetryPolicy.MARKET_ORDERS, SELL, data) { key ->
            MarketOrderBody(key, player.uuid.toString(), itemId, MarketOrderBody.SELL, quantity, expectedTotal, accountId)
        }
    }

    /** Buys [quantity] of [itemId], paid from [accountId] (null: the default account). */
    fun buy(player: ServerPlayer, itemId: String, quantity: Int, expectedTotal: Long, accountId: String?) {
        problem(player, itemId, quantity, selling = false)?.let { return cant(player, it) }
        val item = itemOf(itemId) ?: return
        if (accountId != null && PlayerAccounts.accounts(player.uuid)?.accounts?.any { it.id == accountId && it.canSpend } != true) {
            return cant(player, Cant("economy.lnbeconomy.problem.notAllowed"))
        }
        val data = mapOf(
            Data.PLAYER to player.uuid.toString(),
            Data.ITEM to itemId,
            Data.NAME to ItemStack(item).hoverName.string,
            Data.QUANTITY to quantity.toString(),
        )
        TransferJournal.submit(RetryPolicy.MARKET_ORDERS, BUY, data) { key ->
            MarketOrderBody(key, player.uuid.toString(), itemId, MarketOrderBody.BUY, quantity, expectedTotal, accountId)
        }
    }

    private fun finishSell(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue(Data.PLAYER))
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> delayed(server, entry, player, outcome.result)
            is Outcome.Refused -> {
                // Definitive: nothing was sold. Back to the player, through the mailbox if the inventory is full.
                Escrow.release(server, entry, Data.ITEMS, uuid)
                refused(server, entry, player, outcome.result)
            }
            is Outcome.Done -> {
                val result = outcome.decode<MarketOrderResult>()
                settle(server, uuid, result)
                if (result.replayed) LnbEconomy.LOGGER.info("Market sale {} had already been made", entry.key)
                player?.let {
                    say(
                        it, Component.translatable("economy.lnbeconomy.market.sold", result.quantity, name(entry), Texts.amount(result.total, it)).withStyle(ChatFormatting.GREEN),
                        Component.translatable("economy.lnbeconomy.market.toast.sold", result.quantity, name(entry), Amounts.format(result.total, Texts.language(it))).withStyle(ChatFormatting.GREEN),
                    )
                    if (result.capped) say(it, Component.translatable("economy.lnbeconomy.market.capped").withStyle(ChatFormatting.GOLD))
                    it.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1f)
                }
            }
        }
    }

    private fun finishBuy(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue(Data.PLAYER))
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> delayed(server, entry, player, outcome.result)
            is Outcome.Refused -> refused(server, entry, player, outcome.result)
            is Outcome.Done -> {
                val result = outcome.decode<MarketOrderResult>()
                settle(server, uuid, result)
                player?.let {
                    say(
                        it, Component.translatable("economy.lnbeconomy.market.bought", result.quantity, name(entry), Texts.amount(result.total, it)).withStyle(ChatFormatting.GREEN),
                        Component.translatable("economy.lnbeconomy.market.toast.bought", result.quantity, name(entry), Amounts.format(result.total, Texts.language(it))).withStyle(ChatFormatting.GREEN),
                    )
                    it.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1f)
                }
                val mailbox = result.mailboxItemId
                if (mailbox == null) LnbEconomy.LOGGER.error("Market purchase {} has no mailbox item to claim", entry.key)
                else Auctions.claim(uuid, mailbox)
            }
        }
    }

    /** What an order's answer tells us: the account's balance and the item's stock. */
    private fun settle(server: MinecraftServer, uuid: UUID, result: MarketOrderResult) {
        PlayerAccounts.applyBalance(server, result.account.accountId, result.account.balance)
        Markets.state.setStock(result.item, result.stock)
        MarketViews.onStocks(server, listOf(result.item))
        MarketViews.afterOrder(server, uuid)
    }

    private fun name(entry: JournalEntry): Component = Component.literal(entry.data[Data.NAME].orEmpty())

    private fun delayed(server: MinecraftServer, entry: JournalEntry, player: ServerPlayer?, result: ApiResult<*>) {
        if ((result as? ApiResult.Http)?.problem == Problems.IDEMPOTENCY_MISMATCH) {
            // The site has a transaction under this key that isn't this order: a bug. Nothing is given back.
            LnbEconomy.LOGGER.error(
                "Market order {} ({} x{}) was answered idempotencyMismatch: its items and money are kept as they are. Look at the site's ledger, then /economy retry or settle by hand",
                entry.key, entry.data[Data.ITEM], entry.data[Data.QUANTITY],
            )
            for (op in server.playerList.players.filter { it.hasPermissions(2) }) {
                op.sendSystemMessage(Component.translatable("economy.lnbeconomy.market.held", entry.key).withStyle(ChatFormatting.RED))
            }
        }
        player?.let { say(it, Component.translatable("economy.lnbeconomy.market.delayed").withStyle(ChatFormatting.GRAY)) }
    }

    private fun refused(server: MinecraftServer, entry: JournalEntry, player: ServerPlayer?, result: ApiResult<*>) {
        val problem = (result as? ApiResult.Http)?.problem
        // The catalog or the flags moved under us: read them again.
        if (problem in setOf(Problems.NOT_FOUND, Problems.ITEM_DISABLED, Problems.NOT_SELLABLE, Problems.NOT_BUYABLE, Problems.TOO_MANY_ITEMS, Problems.DISABLED)) {
            Markets.refresh(server)
        }
        val target = player ?: return
        if (problem == Problems.PRICE_CHANGED) {
            // The refusal's quote isn't kept by the HTTP client: ask the site for the current price.
            val direction = if (entry.transferDirection() == MarketOrderBody.SELL) MarketOrderBody.SELL else MarketOrderBody.BUY
            val item = entry.data[Data.ITEM].orEmpty()
            val quantity = entry.data[Data.QUANTITY]?.toIntOrNull() ?: 1
            EconomyApi.marketQuote(MarketQuoteBody(target.uuid.toString(), item, direction, quantity)).thenAccept { quote ->
                server.execute {
                    if (target.hasDisconnected()) return@execute
                    val now = (quote as? ApiResult.Ok)?.body?.total
                    say(
                        target, (if (now != null) Component.translatable("economy.lnbeconomy.market.price_changed", Texts.amount(now, target))
                        else Component.translatable("economy.lnbeconomy.problem.priceChanged")).withStyle(ChatFormatting.RED),
                    )
                }
            }
            return
        }
        val reason = when (problem) {
            Problems.ZERO_PAYOUT -> Component.translatable("economy.lnbeconomy.problem.zeroPayout")
            else -> Texts.problem(result)
        }
        say(target, Component.translatable("economy.lnbeconomy.market.refused", reason).withStyle(ChatFormatting.RED))
        target.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.5f)
    }

    private fun JournalEntry.transferDirection(): String? = body.get("direction")?.asString

    /** Chat and the screen's toast: red is a failure, anything else is good news or a note. */
    private fun say(player: ServerPlayer, message: Component, toast: Component = message) {
        player.sendSystemMessage(message)
        MarketViews.toast(player, message.style.color?.value != ChatFormatting.RED.color, toast)
    }

    private fun cant(player: ServerPlayer, why: Cant) {
        val args = why.args.map { if (it is Component || it is Number) it else it.toString() }.toTypedArray()
        say(player, Component.translatable("economy.lnbeconomy.market.cant", Component.translatable(why.key, *args)).withStyle(ChatFormatting.RED))
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.5f)
    }
}

package fr.bde_teknantes.lnb.economy.shop

import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.account.RealPlayers
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.api.ServerShop
import fr.bde_teknantes.lnb.economy.api.ShopDisableBody
import fr.bde_teknantes.lnb.economy.api.ShopItem
import fr.bde_teknantes.lnb.economy.api.ShopOrderBody
import fr.bde_teknantes.lnb.economy.api.ShopPosition
import fr.bde_teknantes.lnb.economy.api.ShopSettingsBody
import fr.bde_teknantes.lnb.economy.item.Escrow
import fr.bde_teknantes.lnb.economy.item.ItemStacks
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.mail.ItemMailbox
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.OpenShopPayload
import fr.bde_teknantes.lnb.economy.network.ShopOrderPayload
import fr.bde_teknantes.lnb.economy.network.ShopSavePayload
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * What shops do on the server. Every order takes the items aside first (the shop's for a purchase, the player's for
 * a sale back), saves them in the journal entry, then asks the site, which prices the order from the shop's saved
 * prices. The entry's handler hands the items over, or back, whatever the answer.
 */
object Shops {
    private const val ORDER = "shopOrder"
    private const val DISABLE = "shopDisable"
    private const val REACH_SQR = 8.0 * 8.0
    private const val MAX_LOTS = 10_000

    /** The player's main inventory, without armor and offhand. */
    private val MAIN_INVENTORY = 0 until 36

    private object Data {
        const val PLAYER = "player"
        const val DIRECTION = "direction"
        const val ITEMS = "items"
        const val WORLD = "world"
        const val X = "x"
        const val Y = "y"
        const val Z = "z"
        const val SHOP = "shop"
        const val OWNER = "owner"
        const val ITEM_NAME = "itemName"
        const val COUNT = "count"
    }

    fun register() {
        TransferJournal.register(ORDER, ::finishOrder)
        // Nothing to finish: the site turned the shop off, or it was already off.
        TransferJournal.register(DISABLE) { _, _, _ -> }
    }

    /** Owners and members of the shop's account run it; before it has one, whoever placed it. Creative operators too. */
    fun canManage(player: ServerPlayer, shop: ShopBlockEntity): Boolean {
        if (player.isCreative && player.hasPermissions(2)) return true
        val accountId = shop.accountId ?: return shop.placedBy == null || shop.placedBy == player.uuid
        return PlayerAccounts.accounts(player.uuid)?.accounts?.any { it.id == accountId && it.canSpend } == true
    }

    fun use(player: ServerPlayer, pos: BlockPos) {
        if (!RealPlayers.isReal(player)) return
        if (!EconomyConfig.SHOPS.get()) {
            tell(player, Component.translatable("economy.lnbeconomy.shop.switched_off"))
            return
        }
        val server = player.server
        // Roles decide which screen opens: read them first (from the cache when it is fresh).
        PlayerAccounts.refresh(server, player.uuid, force = false).whenComplete { _, _ ->
            server.execute {
                val shop = shopAt(player, pos) ?: return@execute
                if (canManage(player, shop) && !player.isShiftKeyDown) openSettings(player, shop) else openCustomer(player, shop, refresh = false)
            }
        }
    }

    private fun openSettings(player: ServerPlayer, shop: ShopBlockEntity) {
        val level = shop.level ?: return
        val setup = ShopSetup(shop.blockPos, shop.settings, shop.accountId, shop.shopId != null, shop.offOnSite)
        val template = object : SimpleContainer(shop.template.copy()) {
            override fun setChanged() {
                super.setChanged()
                shop.template = getItem(0).copy()
                shop.setChanged()
            }
        }
        player.openMenu(
            SimpleMenuProvider(
                { id, inventory, _ -> ShopMenu(id, inventory, shop.stock, template, setup, ContainerLevelAccess.create(level, shop.blockPos)) },
                Component.translatable("gui.lnbeconomy.shop.settings"),
            ),
        ) { buf -> setup.write(buf) }
    }

    private fun openCustomer(player: ServerPlayer, shop: ShopBlockEntity, refresh: Boolean) {
        val settings = shop.settings
        if (shop.shopId == null || settings == null || shop.template.isEmpty) {
            if (!refresh) tell(player, Component.translatable("economy.lnbeconomy.shop.not_set_up"))
            return
        }
        if (shop.offOnSite) {
            if (!refresh) tell(player, Component.translatable("economy.lnbeconomy.shop.closed"))
            return
        }
        val inStock = Stock.count(shop.stock, shop.template)
        val room = if (settings.buyPrice == null) 0 else minOf(Stock.room(shop.stock, shop.template), settings.buyCap - inStock).coerceAtLeast(0)
        val seller = shop.accountHandle?.let(Handles::display) ?: shop.placedBy?.let { nameOf(player.server, it) }.orEmpty()
        PacketDistributor.sendToPlayer(
            player,
            OpenShopPayload(shop.blockPos, shop.template, settings.lotSize, settings.sellPrice, settings.buyPrice ?: 0, inStock, room, seller, refresh),
        )
        if (!refresh) checkOnSite(player.server, shop)
    }

    /** Learns whether an admin turned the shop off on the site since it was last saved. */
    private fun checkOnSite(server: MinecraftServer, shop: ShopBlockEntity) {
        val id = shop.shopId ?: return
        EconomyApi.shop(id).thenAccept { result ->
            if (result !is ApiResult.Ok) return@thenAccept
            server.execute {
                val off = result.body.disabled && result.body.disabledReason == ServerShop.DISABLED_BY_ADMIN
                if (shop.offOnSite != off && shop.shopId == id) {
                    shop.offOnSite = off
                    shop.setChanged()
                }
            }
        }
    }

    fun handleSave(payload: ShopSavePayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val shop = shopAt(player, payload.pos) ?: return
        if (!canManage(player, shop)) return
        val settings = ShopSettings(payload.lotSize, payload.sellPrice, payload.buyPrice.takeIf { it > 0 }, payload.buyCap)
        settings.problem(shop.template)?.let {
            tell(player, Component.translatable("economy.lnbeconomy.shop.invalid.$it"))
            return
        }
        val account = PlayerAccounts.accounts(player.uuid)?.accounts?.firstOrNull { it.id == payload.accountId && it.canSpend }
        if (account == null) {
            tell(player, Component.translatable("economy.lnbeconomy.problem.notAllowed"))
            return
        }
        val server = player.server
        val template = shop.template.copy()
        val body = ShopSettingsBody(
            player = player.uuid.toString(),
            account = account.id,
            item = ShopItem(
                id = BuiltInRegistries.ITEM.getKey(template.item).toString(),
                snbt = ItemStacks.toTag(template, server.registryAccess()).toString(),
                name = template.hoverName.string.take(64).ifBlank { BuiltInRegistries.ITEM.getKey(template.item).path },
            ),
            lotSize = settings.lotSize,
            sellPrice = settings.sellPrice,
            buyPrice = settings.buyPrice,
            position = ShopPosition(player.level().dimension().location().toString(), shop.blockPos.x, shop.blockPos.y, shop.blockPos.z),
        )
        val id = shop.shopId
        val call = if (id == null) EconomyApi.createShop(body) else EconomyApi.updateShop(id, body)
        call.thenAccept { result ->
            server.execute {
                when (result) {
                    is ApiResult.Ok -> {
                        shop.shopId = result.body.id
                        shop.settings = settings
                        shop.accountId = result.body.account.id
                        shop.accountHandle = result.body.account.handle
                        shop.offOnSite = false
                        shop.setChanged()
                        tell(player, Component.translatable("economy.lnbeconomy.shop.saved").withStyle(ChatFormatting.GREEN))
                    }
                    else -> {
                        if ((result as? ApiResult.Http)?.problem == Problems.SHOP_DISABLED) {
                            shop.offOnSite = true
                            shop.setChanged()
                        }
                        tell(player, Component.translatable("economy.lnbeconomy.shop.save_refused", Texts.problem(result)))
                    }
                }
            }
        }
    }

    fun handleOrder(payload: ShopOrderPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!RealPlayers.isReal(player) || !EconomyConfig.SHOPS.get()) return
        val shop = shopAt(player, payload.pos) ?: return
        val settings = shop.settings
        val shopId = shop.shopId
        if (!shop.isOpen || settings == null || shopId == null) {
            tell(player, Component.translatable("economy.lnbeconomy.shop.closed"))
            return
        }
        if (payload.lots !in 1..MAX_LOTS) return
        PlayerAccounts.noEconomyMessage(player.uuid)?.let {
            tell(player, it)
            return
        }
        val lotPrice = if (payload.buy) settings.sellPrice else settings.buyPrice
        if (lotPrice == null) {
            tell(player, Component.translatable("economy.lnbeconomy.problem.noBuyBack"))
            return
        }
        if (lotPrice != payload.expectedLotPrice) {
            tell(player, Component.translatable("economy.lnbeconomy.problem.priceChanged"))
            openCustomer(player, shop, refresh = true)
            return
        }
        if (payload.accountId != null && PlayerAccounts.accounts(player.uuid)?.accounts?.any { it.id == payload.accountId && it.canSpend } != true) {
            tell(player, Component.translatable("economy.lnbeconomy.problem.notAllowed"))
            return
        }
        val count = payload.lots * settings.lotSize
        val taken = if (payload.buy) {
            Stock.take(shop.stock, shop.template, count) ?: run {
                tell(player, Component.translatable("economy.lnbeconomy.shop.not_enough_stock"))
                return
            }
        } else {
            val inStock = Stock.count(shop.stock, shop.template)
            if (count > minOf(Stock.room(shop.stock, shop.template), settings.buyCap - inStock)) {
                tell(player, Component.translatable("economy.lnbeconomy.shop.full"))
                return
            }
            Stock.take(player.inventory, shop.template, count, MAIN_INVENTORY) ?: run {
                tell(player, Component.translatable("economy.lnbeconomy.shop.not_enough_items"))
                return
            }
        }
        val level = player.serverLevel()
        val data = buildMap {
            put(Data.PLAYER, player.uuid.toString())
            put(Data.DIRECTION, if (payload.buy) ShopOrderBody.BUY else ShopOrderBody.SELL)
            put(Data.ITEMS, Escrow.hold(taken, player.server.registryAccess()))
            put(Data.WORLD, level.dimension().location().toString())
            put(Data.X, shop.blockPos.x.toString())
            put(Data.Y, shop.blockPos.y.toString())
            put(Data.Z, shop.blockPos.z.toString())
            put(Data.SHOP, shopId)
            shop.placedBy?.let { put(Data.OWNER, it.toString()) }
            put(Data.ITEM_NAME, shop.template.hoverName.string)
            put(Data.COUNT, count.toString())
        }
        TransferJournal.submit("/shops/$shopId/orders", ORDER, data) { key ->
            ShopOrderBody(
                idempotencyKey = key,
                player = player.uuid.toString(),
                account = payload.accountId,
                direction = if (payload.buy) ShopOrderBody.BUY else ShopOrderBody.SELL,
                lots = payload.lots,
                expectedLotPrice = lotPrice,
            )
        }
        openCustomer(player, shop, refresh = true)
    }

    private fun finishOrder(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val customer = UUID.fromString(entry.data.getValue(Data.PLAYER))
        val buying = entry.data[Data.DIRECTION] == ShopOrderBody.BUY
        val player = server.playerList.getPlayer(customer)
        val itemName = Component.literal(entry.data[Data.ITEM_NAME].orEmpty())
        val count = entry.data[Data.COUNT].orEmpty()
        when (outcome) {
            is Outcome.Delayed -> player?.let { tell(it, Component.translatable("economy.lnbeconomy.shop.delayed").withStyle(ChatFormatting.GRAY)) }
            is Outcome.Refused -> {
                if ((outcome.result as? ApiResult.Http)?.problem == Problems.SHOP_DISABLED) {
                    shopOf(server, entry)?.let {
                        it.offOnSite = true
                        it.setChanged()
                    }
                }
                if (buying) backToShop(server, entry) else Escrow.release(server, entry, Data.ITEMS, customer)
                player?.let { tell(it, Component.translatable("economy.lnbeconomy.shop.refused", Texts.problem(outcome.result)).withStyle(ChatFormatting.RED)) }
            }
            is Outcome.Done -> {
                val result = outcome.result
                PlayerAccounts.applyTransfer(server, result)
                if (buying) Escrow.release(server, entry, Data.ITEMS, customer) else backToShop(server, entry)
                player?.let {
                    val key = if (buying) "economy.lnbeconomy.shop.bought" else "economy.lnbeconomy.shop.sold"
                    tell(it, Component.translatable(key, count, itemName, Texts.amount(result.amount, it)).withStyle(ChatFormatting.GREEN))
                }
                val shopAccount = if (buying) result.to.accountId else result.from.accountId
                val customerName = player?.gameProfile?.name ?: nameOf(server, customer)
                val notices = ShopNotices.get(server)
                for (holder in PlayerAccounts.holdersOf(server, shopAccount)) {
                    if (holder.uuid == customer || !notices.wants(holder.uuid)) continue
                    val key = if (buying) "economy.lnbeconomy.shop.notice.bought" else "economy.lnbeconomy.shop.notice.sold"
                    holder.sendSystemMessage(
                        Component.translatable(key, customerName, count, itemName, Texts.amount(result.amount, holder)).withStyle(ChatFormatting.GRAY),
                    )
                }
                shopOf(server, entry)?.let { shop -> player?.let { if (it.distanceToSqr(shop.blockPos.center) <= REACH_SQR) openCustomer(it, shop, refresh = true) } }
            }
        }
    }

    /**
     * Puts the entry's escrowed items in its shop's stock. What doesn't fit, or everything when the shop is gone, goes to
     * the mailbox of whoever placed the shop.
     */
    private fun backToShop(server: MinecraftServer, entry: JournalEntry) {
        val saved = Escrow.saved(entry, Data.ITEMS)
        val owner = entry.data[Data.OWNER]?.let(UUID::fromString) ?: UUID.fromString(entry.data.getValue(Data.PLAYER))
        val shop = shopOf(server, entry)
        if (shop == null) {
            ItemMailbox.deliverSaved(server, owner, saved)
            return
        }
        val read = ItemStacks.fromTags(saved, server.registryAccess())
        val left = Stock.insert(shop.stock, read.stacks)
        ItemMailbox.deliverSaved(server, owner, ItemStacks.toTags(left, server.registryAccess()) + read.unreadable)
    }

    /** The shop an entry was made at, if its block is still there and still the same shop. */
    private fun shopOf(server: MinecraftServer, entry: JournalEntry): ShopBlockEntity? {
        val world = ResourceLocation.tryParse(entry.data[Data.WORLD].orEmpty()) ?: return null
        val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, world)) ?: return null
        val pos = BlockPos(
            entry.data[Data.X]?.toIntOrNull() ?: return null,
            entry.data[Data.Y]?.toIntOrNull() ?: return null,
            entry.data[Data.Z]?.toIntOrNull() ?: return null,
        )
        return (level.getBlockEntity(pos) as? ShopBlockEntity)?.takeIf { it.shopId == entry.data[Data.SHOP] }
    }

    /** A broken shop stops selling on the site too. Through the journal, so it gets there even if the site is down. */
    fun onRemoved(shop: ShopBlockEntity) {
        val id = shop.shopId ?: return
        TransferJournal.submit("/shops/$id/disable", DISABLE) { key -> ShopDisableBody(key, null) }
    }

    /** Only a shop's owners and members break it. */
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        val shop = level.getBlockEntity(event.pos) as? ShopBlockEntity ?: return
        val player = event.player as? ServerPlayer
        if (player == null || !RealPlayers.isReal(player) || !canManage(player, shop)) {
            event.isCanceled = true
            player?.let { tell(it, Component.translatable("economy.lnbeconomy.shop.not_yours")) }
        }
    }

    private fun shopAt(player: ServerPlayer, pos: BlockPos): ShopBlockEntity? {
        if (player.distanceToSqr(pos.center) > REACH_SQR) return null
        return player.serverLevel().getBlockEntity(pos) as? ShopBlockEntity
    }

    private fun nameOf(server: MinecraftServer, uuid: UUID): String =
        server.profileCache?.get(uuid)?.orElse(null)?.name ?: uuid.toString().take(8)

    private fun tell(player: ServerPlayer, message: Component) = player.sendSystemMessage(message)
}

package fr.bde_teknantes.lnb.economy

import com.mojang.serialization.Codec
import fr.bde_teknantes.lnb.economy.auction.AuctionBlock
import fr.bde_teknantes.lnb.economy.auction.Auctioneer
import fr.bde_teknantes.lnb.economy.casino.CasinoDealer
import fr.bde_teknantes.lnb.economy.casino.LinkToolItem
import fr.bde_teknantes.lnb.economy.chip.ChipItem
import fr.bde_teknantes.lnb.economy.chip.Denominations
import fr.bde_teknantes.lnb.economy.counter.CounterBlock
import fr.bde_teknantes.lnb.economy.counter.CounterBlockEntity
import fr.bde_teknantes.lnb.economy.poker.PokerTableBlock
import fr.bde_teknantes.lnb.economy.poker.PokerTableBlockEntity
import fr.bde_teknantes.lnb.economy.shop.ShopBlock
import fr.bde_teknantes.lnb.economy.shop.ShopBlockEntity
import fr.bde_teknantes.lnb.economy.shop.ShopMenu
import fr.bde_teknantes.lnb.economy.trade.TradeMenu
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.item.BlockItem
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Items, blocks, block entities, menus and data components. */
object ModContent {
    private val ITEMS = DeferredRegister.createItems(LnbEconomy.ID)
    private val BLOCKS = DeferredRegister.createBlocks(LnbEconomy.ID)
    private val COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LnbEconomy.ID)
    private val BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LnbEconomy.ID)
    private val MENUS = DeferredRegister.create(Registries.MENU, LnbEconomy.ID)
    private val ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, LnbEconomy.ID)
    private val TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LnbEconomy.ID)

    /** The casino a chip belongs to: its account's handle, without the `#`. */
    val CASINO: Supplier<DataComponentType<String>> = COMPONENTS.registerComponentType("casino") { builder ->
        builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    }

    /** One item per chip value, in [Denominations.ALL] order. */
    val CHIPS: Map<Int, DeferredItem<ChipItem>> = Denominations.ALL.associateWith { value ->
        ITEMS.registerItem("chip_$value", { properties -> ChipItem(value, properties) }, Item.Properties())
    }

    /** A 3 x 2 wall (see CounterBlock): light passes around it, and pistons can't push it apart. */
    val COUNTER: DeferredBlock<CounterBlock> = BLOCKS.registerBlock("counter", ::CounterBlock,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 1200f).sound(SoundType.WOOD).noOcclusion()
            .pushReaction(PushReaction.BLOCK))

    val COUNTER_ITEM: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem(COUNTER)

    val COUNTER_ENTITY: Supplier<BlockEntityType<CounterBlockEntity>> = BLOCK_ENTITIES.register("counter", Supplier {
        BlockEntityType.Builder.of(::CounterBlockEntity, COUNTER.get()).build(null)
    })

    /** Breaking takes a while, explosions do nothing, and pistons can't push it. Only its owners break it (see Shops). */
    val SHOP: DeferredBlock<ShopBlock> = BLOCKS.registerBlock("shop", ::ShopBlock,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 3_600_000f).sound(SoundType.WOOD).noOcclusion()
            .pushReaction(PushReaction.BLOCK))

    val SHOP_ITEM: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem(SHOP)

    val SHOP_ENTITY: Supplier<BlockEntityType<ShopBlockEntity>> = BLOCK_ENTITIES.register("shop", Supplier {
        BlockEntityType.Builder.of(::ShopBlockEntity, SHOP.get()).build(null)
    })

    /** A 4 x 3 table (see PokerTableBlock): light passes around it, and pistons can't push it apart. */
    val POKER_TABLE: DeferredBlock<PokerTableBlock> = BLOCKS.registerBlock("poker_table", ::PokerTableBlock,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5f, 1200f).sound(SoundType.WOOD).noOcclusion()
            .pushReaction(PushReaction.BLOCK))

    val POKER_TABLE_ITEM: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem(POKER_TABLE)

    val POKER_ENTITY: Supplier<BlockEntityType<PokerTableBlockEntity>> = BLOCK_ENTITIES.register("poker_table", Supplier {
        BlockEntityType.Builder.of(::PokerTableBlockEntity, POKER_TABLE.get()).build(null)
    })

    val SHOP_MENU: Supplier<MenuType<ShopMenu>> = MENUS.register("shop", Supplier { IMenuTypeExtension.create(::ShopMenu) })

    val TRADE_MENU: Supplier<MenuType<TradeMenu>> = MENUS.register("trade", Supplier { IMenuTypeExtension.create(::TradeMenu) })

    /** Unbreakable outside creative mode, like bedrock, and drops nothing: operators place it. */
    val AUCTION: DeferredBlock<AuctionBlock> = BLOCKS.registerBlock("auction_house", ::AuctionBlock,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(-1f, 3_600_000f).sound(SoundType.WOOD).noLootTable()
            .pushReaction(PushReaction.BLOCK))

    val AUCTION_ITEM: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem(AUCTION)

    val AUCTIONEER: Supplier<EntityType<Auctioneer>> = ENTITIES.register("auctioneer", Supplier {
        EntityType.Builder.of(::Auctioneer, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("auctioneer")
    })

    val CASINO_DEALER: Supplier<EntityType<CasinoDealer>> = ENTITIES.register("casino_dealer", Supplier {
        EntityType.Builder.of(::CasinoDealer, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("casino_dealer")
    })

    val LINK_TOOL: DeferredItem<LinkToolItem> = ITEMS.registerItem("casino_link_tool", ::LinkToolItem, Item.Properties().stacksTo(1))

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        COMPONENTS.register(modBus)
        BLOCK_ENTITIES.register(modBus)
        MENUS.register(modBus)
        ENTITIES.register(modBus)
        TABS.register(modBus)
        modBus.addListener<EntityAttributeCreationEvent> {
            it.put(AUCTIONEER.get(), Auctioneer.attributes().build())
            it.put(CASINO_DEALER.get(), CasinoDealer.attributes().build())
        }
    }

    /**
     * Every item of the mod in its own "LNB Economy" tab: the link wand, then the counter, the poker table and the shop.
     * The auction house is placed by operators only, so it shows for those who have the permissions. Chips are only made
     * by counters: a chip without a casino is worth nothing, so none in the tab.
     */
    private val CREATIVE_TAB: Supplier<CreativeModeTab> = TABS.register("lnbeconomy", Supplier {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lnbeconomy"))
            .icon { ItemStack(COUNTER_ITEM.get()) }
            .displayItems { parameters, output ->
                output.accept(LINK_TOOL.get())
                output.accept(COUNTER_ITEM.get())
                output.accept(POKER_TABLE_ITEM.get())
                output.accept(SHOP_ITEM.get())
                if (parameters.hasPermissions()) output.accept(AUCTION_ITEM.get())
            }
            .build()
    })
}

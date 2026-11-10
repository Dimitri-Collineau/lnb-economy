package fr.bde_teknantes.lnb.economy

import com.mojang.serialization.Codec
import fr.bde_teknantes.lnb.economy.casino.LinkToolItem
import fr.bde_teknantes.lnb.economy.chip.ChipItem
import fr.bde_teknantes.lnb.economy.chip.Denominations
import fr.bde_teknantes.lnb.economy.counter.CounterBlock
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Items, blocks and data components. */
object ModContent {
    private val ITEMS = DeferredRegister.createItems(LnbEconomy.ID)
    private val BLOCKS = DeferredRegister.createBlocks(LnbEconomy.ID)
    private val COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LnbEconomy.ID)

    /** The casino a chip belongs to: its account's handle, without the `#`. */
    val CASINO: Supplier<DataComponentType<String>> = COMPONENTS.registerComponentType("casino") { builder ->
        builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    }

    /** One item per chip value, in [Denominations.ALL] order. */
    val CHIPS: Map<Int, DeferredItem<ChipItem>> = Denominations.ALL.associateWith { value ->
        ITEMS.registerItem("chip_$value", { properties -> ChipItem(value, properties) }, Item.Properties())
    }

    val COUNTER: DeferredBlock<CounterBlock> = BLOCKS.registerBlock("counter", ::CounterBlock,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f, 1200f).sound(SoundType.WOOD))

    val COUNTER_ITEM: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem(COUNTER)

    val LINK_TOOL: DeferredItem<LinkToolItem> = ITEMS.registerItem("casino_link_tool", ::LinkToolItem, Item.Properties().stacksTo(1))

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        COMPONENTS.register(modBus)
        modBus.addListener(::addToCreativeTabs)
    }

    private fun addToCreativeTabs(event: BuildCreativeModeTabContentsEvent) {
        // Chips are only made by counters: a chip without a casino is worth nothing, so none in the creative tabs.
        when (event.tabKey) {
            CreativeModeTabs.TOOLS_AND_UTILITIES -> event.accept(LINK_TOOL.get())
            CreativeModeTabs.FUNCTIONAL_BLOCKS -> event.accept(COUNTER_ITEM.get())
        }
    }
}

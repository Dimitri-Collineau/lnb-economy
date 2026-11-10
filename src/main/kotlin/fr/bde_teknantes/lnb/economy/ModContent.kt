package fr.bde_teknantes.lnb.economy

import com.mojang.serialization.Codec
import fr.bde_teknantes.lnb.economy.chip.ChipItem
import fr.bde_teknantes.lnb.economy.chip.Denominations
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Items, blocks and data components. */
object ModContent {
    private val ITEMS = DeferredRegister.createItems(LnbEconomy.ID)
    private val COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LnbEconomy.ID)

    /** The casino a chip belongs to: its account's handle, without the `#`. */
    val CASINO: Supplier<DataComponentType<String>> = COMPONENTS.registerComponentType("casino") { builder ->
        builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    }

    /** One item per chip value, in [Denominations.ALL] order. */
    val CHIPS: Map<Int, DeferredItem<ChipItem>> = Denominations.ALL.associateWith { value ->
        ITEMS.registerItem("chip_$value", { properties -> ChipItem(value, properties) }, Item.Properties())
    }

    fun register(modBus: IEventBus) {
        ITEMS.register(modBus)
        COMPONENTS.register(modBus)
        modBus.addListener(::addToCreativeTabs)
    }

    private fun addToCreativeTabs(event: BuildCreativeModeTabContentsEvent) {
        // Chips are only made by counters: a chip without a casino is worth nothing, so none in the creative tabs.
        if (event.tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            extraTools.forEach { event.accept(it.get()) }
        }
    }

    /** Tools added by other parts of the mod (the link wand, the counter). */
    internal val extraTools = mutableListOf<Supplier<out Item>>()
}

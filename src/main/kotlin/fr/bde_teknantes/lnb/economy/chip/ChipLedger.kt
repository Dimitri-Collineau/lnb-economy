package fr.bde_teknantes.lnb.economy.chip

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/**
 * The value of the chips each casino has in circulation, which its account backs: a counter selling chips or a
 * machine paying out adds to it, a machine taking a bet or a counter cashing chips takes from it.
 * Stored in the overworld's data folder as `lnbeconomy_chips.dat`.
 */
class ChipLedger private constructor() : SavedData() {
    private val outstanding = HashMap<String, Long>()

    fun outstanding(handle: String): Long = outstanding[handle] ?: 0

    val all: Map<String, Long> get() = outstanding.toMap()

    /** Chips of [handle] worth [value] were made (positive) or destroyed (negative). Never goes below 0. */
    fun add(handle: String, value: Long) {
        if (value == 0L) return
        val next = outstanding(handle) + value
        if (next < 0) {
            // More chips came back than the ledger knew of: chips made before the ledger, or duplicated.
            LnbEconomy.LOGGER.warn("Chips of #{} came back for {} more than were in circulation", handle, -next)
        }
        outstanding[handle] = maxOf(0, next)
        setDirty()
    }

    /** For operators fixing the count after chips were lost where the mod can't see it. */
    fun set(handle: String, value: Long) {
        outstanding[handle] = maxOf(0, value)
        setDirty()
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val casinos = CompoundTag()
        outstanding.forEach { (handle, value) -> casinos.putLong(handle, value) }
        tag.put("outstanding", casinos)
        return tag
    }

    companion object {
        private const val NAME = "lnbeconomy_chips"

        fun get(server: MinecraftServer): ChipLedger =
            server.overworld().dataStorage.computeIfAbsent(Factory(::ChipLedger, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): ChipLedger {
            val ledger = ChipLedger()
            val casinos = tag.getCompound("outstanding")
            casinos.allKeys.forEach { ledger.outstanding[it] = casinos.getLong(it) }
            return ledger
        }
    }
}

package fr.bde_teknantes.lnb.economy.casino

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Which casino each machine and counter belongs to, keyed by dimension and position (a machine's anchor).
 * Stored in the overworld's data folder as `lnbeconomy_links.dat`.
 */
class CasinoLinks private constructor() : SavedData() {
    /**
     * @param handle the casino's account, without `#`
     * @param maxBet the largest stake a machine takes in one game, null for no limit but the casino's free balance
     */
    data class Link(val handle: String, val maxBet: Long?, val linkedBy: UUID, val linkedByName: String)

    data class Key(val dimension: ResourceKey<Level>, val pos: BlockPos)

    private val links = HashMap<Key, Link>()

    val handles: Set<String> get() = links.values.mapTo(HashSet()) { it.handle }

    fun get(dimension: ResourceKey<Level>, pos: BlockPos): Link? = links[Key(dimension, pos)]

    fun put(dimension: ResourceKey<Level>, pos: BlockPos, link: Link) {
        links[Key(dimension, pos.immutable())] = link
        setDirty()
    }

    fun remove(dimension: ResourceKey<Level>, pos: BlockPos): Link? = links.remove(Key(dimension, pos))?.also { setDirty() }

    fun linkedTo(handle: String): List<Key> = links.filterValues { it.handle == handle }.keys.toList()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        for ((key, link) in links) {
            list.add(CompoundTag().apply {
                putString("dimension", key.dimension.location().toString())
                putLong("pos", key.pos.asLong())
                putString("handle", link.handle)
                link.maxBet?.let { putLong("maxBet", it) }
                putUUID("linkedBy", link.linkedBy)
                putString("linkedByName", link.linkedByName)
            })
        }
        tag.put("links", list)
        return tag
    }

    companion object {
        private const val NAME = "lnbeconomy_links"

        fun get(server: MinecraftServer): CasinoLinks =
            server.overworld().dataStorage.computeIfAbsent(Factory(::CasinoLinks, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): CasinoLinks {
            val data = CasinoLinks()
            val list = tag.getList("links", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val entry = list.getCompound(i)
                val dimension = ResourceLocation.tryParse(entry.getString("dimension")) ?: continue
                if (!entry.hasUUID("linkedBy")) continue
                val key = Key(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(entry.getLong("pos")))
                data.links[key] = Link(
                    handle = entry.getString("handle"),
                    maxBet = if (entry.contains("maxBet")) entry.getLong("maxBet") else null,
                    linkedBy = entry.getUUID("linkedBy"),
                    linkedByName = entry.getString("linkedByName"),
                )
            }
            return data
        }
    }
}

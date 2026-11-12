package fr.bde_teknantes.lnb.economy.shop

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Players who turned off the chat messages about their sales (`/economy notify off`): the one sent to a shop's owners
 * and members at each order, and the one sent to the seller of an auction house listing.
 * Stored in the overworld's data folder as `lnbeconomy_notices.dat`.
 */
class ShopNotices private constructor() : SavedData() {
    private val muted = HashSet<UUID>()

    fun wants(player: UUID): Boolean = player !in muted

    fun set(player: UUID, on: Boolean) {
        val changed = if (on) muted.remove(player) else muted.add(player)
        if (changed) setDirty()
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put(MUTED, ListTag().apply { muted.forEach { add(StringTag.valueOf(it.toString())) } })
        return tag
    }

    companion object {
        private const val NAME = "lnbeconomy_notices"
        private const val MUTED = "muted"

        fun get(server: MinecraftServer): ShopNotices =
            server.overworld().dataStorage.computeIfAbsent(Factory(::ShopNotices, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): ShopNotices {
            val notices = ShopNotices()
            val list = tag.getList(MUTED, Tag.TAG_STRING.toInt())
            for (i in 0 until list.size) runCatching { UUID.fromString(list.getString(i)) }.getOrNull()?.let(notices.muted::add)
            return notices
        }
    }
}

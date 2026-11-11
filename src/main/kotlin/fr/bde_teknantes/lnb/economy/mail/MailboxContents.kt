package fr.bde_teknantes.lnb.economy.mail

import fr.bde_teknantes.lnb.economy.item.ItemStacks
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * What the mailbox holds for each player, without the server around it. Stacks are kept as saved tags, so a stack the
 * game can't read any more stays here, untouched, instead of being lost.
 */
class MailboxContents {
    /** What [take] gave, and what is still waiting. */
    data class Taken(val given: Int, val waiting: Int, val unreadable: Int)

    private val held = LinkedHashMap<UUID, MutableList<CompoundTag>>()

    fun count(player: UUID): Int = held[player]?.sumOf { it.getInt(COUNT).coerceAtLeast(1) } ?: 0

    fun add(player: UUID, stacks: Iterable<ItemStack>, registries: HolderLookup.Provider): Boolean =
        addSaved(player, ItemStacks.toTags(stacks, registries))

    /** Adds stacks as saved tags, read or not: a stack the game can't read is kept for when it can again. */
    fun addSaved(player: UUID, tags: List<CompoundTag>): Boolean {
        if (tags.isEmpty()) return false
        held.getOrPut(player, ::mutableListOf).addAll(tags)
        return true
    }

    /**
     * Hands the player's stacks to [insert], which takes what it can from each stack and leaves the rest in it (as
     * `Inventory.add` does). What it leaves stays here.
     */
    fun take(player: UUID, registries: HolderLookup.Provider, insert: (ItemStack) -> Unit): Taken {
        val tags = held.remove(player) ?: return Taken(0, 0, 0)
        val kept = mutableListOf<CompoundTag>()
        var given = 0
        var unreadable = 0
        for (tag in tags) {
            val stack = ItemStacks.fromTag(tag, registries)
            if (stack == null) {
                kept.add(tag)
                unreadable++
                continue
            }
            val before = stack.count
            insert(stack)
            given += before - stack.count
            if (!stack.isEmpty) kept.add(ItemStacks.toTag(stack, registries)!!)
        }
        if (kept.isNotEmpty()) held[player] = kept
        return Taken(given, kept.sumOf { it.getInt(COUNT).coerceAtLeast(1) }, unreadable)
    }

    fun save(): CompoundTag = CompoundTag().apply {
        held.forEach { (player, tags) -> put(player.toString(), ListTag().apply { addAll(tags) }) }
    }

    fun load(tag: CompoundTag) {
        held.clear()
        for (key in tag.allKeys) {
            val player = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val list = tag.getList(key, Tag.TAG_COMPOUND.toInt())
            if (list.isNotEmpty()) held[player] = MutableList(list.size) { list.getCompound(it) }
        }
    }

    private companion object {
        const val COUNT = "count"
    }
}

package fr.bde_teknantes.lnb.economy.item

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.minecraft.world.item.ItemStack

/**
 * Item stacks as tags and as SNBT text, with every data component, through the game's own codec and the server's
 * registries. Used for items held in escrow in the journal, in the mailbox, and on LNB's site.
 *
 * Reading never throws and never destroys anything: a stack the game can't read any more (an item from a mod that was
 * removed, say) comes back in [Read.unreadable] as it was saved, to be kept or reported.
 */
object ItemStacks {
    private const val ITEMS = "items"

    data class Read(val stacks: List<ItemStack>, val unreadable: List<CompoundTag>)

    /** The stack as a tag, or null for an empty stack. */
    fun toTag(stack: ItemStack, registries: HolderLookup.Provider): CompoundTag? =
        if (stack.isEmpty) null else stack.save(registries) as CompoundTag

    /** The stack saved in [tag], or null when the game can't read it. */
    fun fromTag(tag: CompoundTag, registries: HolderLookup.Provider): ItemStack? =
        ItemStack.parse(registries, tag).orElse(null)?.takeUnless { it.isEmpty }

    fun toTags(stacks: Iterable<ItemStack>, registries: HolderLookup.Provider): List<CompoundTag> =
        stacks.mapNotNull { toTag(it, registries) }

    fun fromTags(tags: Iterable<CompoundTag>, registries: HolderLookup.Provider): Read {
        val stacks = mutableListOf<ItemStack>()
        val unreadable = mutableListOf<CompoundTag>()
        for (tag in tags) {
            val stack = fromTag(tag, registries)
            if (stack == null) unreadable.add(tag) else stacks.add(stack)
        }
        return Read(stacks, unreadable)
    }

    /** The stacks as one line of SNBT. Empty stacks are left out. */
    fun toSnbt(stacks: Iterable<ItemStack>, registries: HolderLookup.Provider): String =
        CompoundTag().apply { put(ITEMS, ListTag().apply { addAll(toTags(stacks, registries)) }) }.toString()

    /** The stacks written by [toSnbt], as saved tags, or null when [snbt] isn't SNBT at all. */
    fun tagsFromSnbt(snbt: String): List<CompoundTag>? {
        val root = runCatching { TagParser.parseTag(snbt) }.getOrNull() ?: return null
        val list = root.getList(ITEMS, Tag.TAG_COMPOUND.toInt())
        return List(list.size) { list.getCompound(it) }
    }

    /** The stacks written by [toSnbt]. Text that isn't SNBT at all reads as nothing. */
    fun fromSnbt(snbt: String, registries: HolderLookup.Provider): Read =
        fromTags(tagsFromSnbt(snbt).orEmpty(), registries)
}

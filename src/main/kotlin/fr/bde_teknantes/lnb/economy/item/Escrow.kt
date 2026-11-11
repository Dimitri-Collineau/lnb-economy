package fr.bde_teknantes.lnb.economy.item

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.mail.ItemMailbox
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Items taken from the world while the site decides on a money move (a shop order, a trade, a listing). They are
 * saved in the journal entry's data before the call, so whatever happens (refusal, crash, restart), the entry's
 * handler can give them to the right player with [release].
 */
object Escrow {
    /** The [data] value that holds [stacks]. */
    fun hold(stacks: Iterable<ItemStack>, registries: HolderLookup.Provider): String = ItemStacks.toSnbt(stacks, registries)

    /** The stacks held under [name] in [entry], as saved tags. */
    fun saved(entry: JournalEntry, name: String): List<CompoundTag> {
        val snbt = entry.data[name] ?: return emptyList()
        return ItemStacks.tagsFromSnbt(snbt) ?: emptyList<CompoundTag>().also {
            LnbEconomy.LOGGER.error("Items held for journal entry {} ({}) can't be read: {}", entry.key, name, snbt)
        }
    }

    /** Gives the stacks held under [name] in [entry] to [player], through the mailbox, so nothing is dropped. */
    fun release(server: MinecraftServer, entry: JournalEntry, name: String, player: UUID) =
        ItemMailbox.deliverSaved(server, player, saved(entry, name))
}

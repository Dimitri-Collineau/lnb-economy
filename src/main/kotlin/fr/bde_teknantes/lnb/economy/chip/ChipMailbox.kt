package fr.bde_teknantes.lnb.economy.chip

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.util.UUID

/**
 * Chips owed to players who left before the site answered (bought chips, or chips handed back after a refused
 * cash-out). Given when they come back. Stored in the overworld's data folder as `lnbeconomy_mailbox.dat`.
 */
class ChipMailbox private constructor() : SavedData() {
    /** Player -> casino -> value. */
    private val owed = HashMap<UUID, HashMap<String, Long>>()

    fun add(player: UUID, handle: String, value: Long) {
        if (value <= 0) return
        owed.getOrPut(player, ::HashMap).merge(handle, value, Long::plus)
        setDirty()
    }

    private fun take(player: UUID): Map<String, Long> = owed.remove(player).orEmpty().also { if (it.isNotEmpty()) setDirty() }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val players = CompoundTag()
        owed.forEach { (player, casinos) ->
            players.put(player.toString(), CompoundTag().apply { casinos.forEach { (handle, value) -> putLong(handle, value) } })
        }
        tag.put("owed", players)
        return tag
    }

    companion object {
        private const val NAME = "lnbeconomy_mailbox"

        fun get(server: MinecraftServer): ChipMailbox =
            server.overworld().dataStorage.computeIfAbsent(Factory(::ChipMailbox, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): ChipMailbox {
            val mailbox = ChipMailbox()
            val players = tag.getCompound("owed")
            for (key in players.allKeys) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                val casinos = players.getCompound(key)
                mailbox.owed[uuid] = casinos.allKeys.associateWithTo(HashMap()) { casinos.getLong(it) }
            }
            return mailbox
        }

        /**
         * Gives [value] of [handle]'s chips to the player when they are online, or keeps them for later.
         * The chips are already counted in circulation.
         */
        fun deliver(server: MinecraftServer, player: UUID, handle: String, value: Long) {
            val online = server.playerList.getPlayer(player)
            if (online == null) get(server).add(player, handle, value) else give(online, handle, value)
        }

        fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
            val player = event.entity as? ServerPlayer ?: return
            get(player.server).take(player.uuid).forEach { (handle, value) -> give(player, handle, value) }
        }

        private fun give(player: ServerPlayer, handle: String, value: Long) {
            for (stack in Chips.make(handle, value)) {
                if (!player.inventory.add(stack)) player.drop(stack, false)
            }
        }
    }
}

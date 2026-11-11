package fr.bde_teknantes.lnb.economy.mail

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.chip.Chips
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.util.UUID

/**
 * Items owed to players: bought chips, items from a shop or a trade, items handed back after the site refused. Given
 * right away when they fit; what doesn't fit, or belongs to a player who is away, waits here until they come back or
 * type `/mailbox`. Nothing is ever dropped on the ground. Stored in the overworld's data folder as
 * `lnbeconomy_mailbox.dat`, and written as soon as it changes, since what it holds already left the journal.
 */
class ItemMailbox private constructor() : SavedData() {
    private val contents = MailboxContents()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put(ITEMS, contents.save())
        return tag
    }

    companion object {
        private const val NAME = "lnbeconomy_mailbox"
        private const val ITEMS = "items"

        /** Chips only, as the mailbox was before it took any item: player -> casino -> value. */
        private const val OLD_CHIPS = "owed"

        private fun get(server: MinecraftServer): ItemMailbox =
            server.overworld().dataStorage.computeIfAbsent(Factory(::ItemMailbox, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): ItemMailbox {
            val mailbox = ItemMailbox()
            mailbox.contents.load(tag.getCompound(ITEMS))
            val chips = tag.getCompound(OLD_CHIPS)
            for (key in chips.allKeys) {
                val player = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                val casinos = chips.getCompound(key)
                for (handle in casinos.allKeys) mailbox.contents.add(player, Chips.make(handle, casinos.getLong(handle)), registries)
            }
            if (!chips.isEmpty) mailbox.setDirty()
            return mailbox
        }

        private fun changed(server: MinecraftServer, mailbox: ItemMailbox) {
            mailbox.setDirty()
            server.overworld().dataStorage.save()
        }

        /** Items the player has waiting. */
        fun count(server: MinecraftServer, player: UUID): Int = get(server).contents.count(player)

        /** Gives [stacks] to the player: what fits right away when they're online, the rest when they collect it. */
        fun deliver(server: MinecraftServer, player: UUID, stacks: List<ItemStack>) {
            val mailbox = get(server)
            if (!mailbox.contents.add(player, stacks, server.registryAccess())) return
            changed(server, mailbox)
            server.playerList.getPlayer(player)?.let { collect(it, quiet = true) }
        }

        /** Gives [value] of [handle]'s chips. The chips are already counted in circulation. */
        fun deliverChips(server: MinecraftServer, player: UUID, handle: String, value: Long) =
            deliver(server, player, Chips.make(handle, value))

        /**
         * Puts what fits in the player's inventory. With [quiet], only says something when items are left waiting;
         * otherwise also says what was collected, or that the mailbox is empty.
         */
        fun collect(player: ServerPlayer, quiet: Boolean): MailboxContents.Taken {
            val server = player.server
            val mailbox = get(server)
            val taken = mailbox.contents.take(player.uuid, server.registryAccess()) { stack -> player.inventory.add(stack) }
            if (taken.given > 0 || taken.waiting > 0) changed(server, mailbox)
            if (taken.unreadable > 0) {
                LnbEconomy.LOGGER.warn("{} item stack(s) in {}'s mailbox can no longer be read and stay there", taken.unreadable, player.gameProfile.name)
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.mailbox.unreadable", taken.unreadable).withStyle(ChatFormatting.RED))
            }
            if (!quiet && taken.given > 0) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.mailbox.collected", taken.given).withStyle(ChatFormatting.GREEN))
            }
            if (taken.waiting > 0) {
                val command = Component.literal("/mailbox").withStyle { style ->
                    style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mailbox"))
                }
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.mailbox.waiting", taken.waiting, command).withStyle(ChatFormatting.GOLD))
            } else if (!quiet && taken.given == 0 && taken.unreadable == 0) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.mailbox.empty"))
            }
            return taken
        }

        fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
            val player = event.entity as? ServerPlayer ?: return
            if (get(player.server).contents.count(player.uuid) > 0) collect(player, quiet = false)
        }

        fun registerCommands(event: RegisterCommandsEvent) {
            for (name in listOf("mailbox", "courrier")) {
                event.dispatcher.register(Commands.literal(name).executes { context ->
                    collect(context.source.playerOrException, quiet = false).given
                })
            }
        }
    }
}

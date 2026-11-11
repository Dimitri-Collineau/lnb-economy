package fr.bde_teknantes.lnb.economy.mail

import net.minecraft.core.HolderLookup
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.BeforeAll
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MailboxContentsTest {
    companion object {
        private lateinit var registries: HolderLookup.Provider

        @JvmStatic
        @BeforeAll
        fun registries() {
            registries = VanillaRegistries.createLookup()
        }
    }

    private val player = UUID.randomUUID()

    /** An inventory with room for [room] items, which takes what fits from each stack. */
    private class Room(var room: Int) {
        val got = mutableListOf<ItemStack>()

        fun insert(stack: ItemStack) {
            val moved = minOf(room, stack.count)
            if (moved == 0) return
            got.add(stack.split(moved))
            room -= moved
        }
    }

    @Test
    fun `what does not fit stays in the mailbox`() {
        val contents = MailboxContents()
        contents.add(player, listOf(ItemStack(Items.DIAMOND, 10), ItemStack(Items.STONE, 64)), registries)

        val room = Room(30)
        val taken = contents.take(player, registries, room::insert)

        assertEquals(MailboxContents.Taken(given = 30, waiting = 44, unreadable = 0), taken)
        assertEquals(44, contents.count(player))

        val rest = Room(100)
        assertEquals(44, contents.take(player, registries, rest::insert).given)
        assertEquals(0, contents.count(player))
        assertEquals(listOf(Items.STONE), rest.got.map { it.item })
    }

    @Test
    fun `stacks the game can't read stay in the mailbox, as they were saved`() {
        val saved = CompoundTag().apply {
            put(player.toString(), ListTag().apply {
                add(CompoundTag().apply { putString("id", "gone:widget"); putInt("count", 2) })
                add(CompoundTag().apply { putString("id", "minecraft:stone"); putInt("count", 5) })
            })
        }
        val contents = MailboxContents().apply { load(saved) }

        val taken = contents.take(player, registries, Room(100)::insert)

        assertEquals(MailboxContents.Taken(given = 5, waiting = 2, unreadable = 1), taken)
        assertEquals("gone:widget", contents.save().getList(player.toString(), 10).getCompound(0).getString("id"))
    }

    @Test
    fun `contents survive a save and a load`() {
        val contents = MailboxContents()
        contents.add(player, listOf(ItemStack(Items.EMERALD, 3)), registries)

        val reloaded = MailboxContents().apply { load(contents.save()) }

        assertEquals(3, reloaded.count(player))
        val room = Room(64)
        reloaded.take(player, registries, room::insert)
        assertEquals(Items.EMERALD, room.got.single().item)
    }

    @Test
    fun `nothing is kept for empty stacks`() {
        val contents = MailboxContents()
        assertEquals(false, contents.add(player, listOf(ItemStack.EMPTY), registries))
        assertEquals(0, contents.count(player))
    }
}

package fr.bde_teknantes.lnb.economy.market

import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeGraphTest {
    private fun raw(output: String, vararg slots: List<String>, count: Int = 1, type: String = "minecraft:crafting_shaped") =
        RecipeGraph.Raw(type, output, count, slots.toList())

    @Test
    fun `repeated ingredients merge into one input`() {
        val graph = RecipeGraph.build(listOf(raw("minecraft:stick", listOf("minecraft:planks"), listOf("minecraft:planks"), count = 4)))
        assertEquals(1, graph.size)
        assertEquals(listOf(RecipeStack("minecraft:planks", 2)), graph[0].inputs)
        assertEquals(RecipeStack("minecraft:stick", 4), graph[0].output)
    }

    @Test
    fun `a tag gives one recipe per item`() {
        val graph = RecipeGraph.build(listOf(raw("minecraft:chest", listOf("a_planks", "b_planks", "c_planks"))))
        assertEquals(listOf("a_planks", "b_planks", "c_planks"), graph.map { it.inputs.single().id })
    }

    @Test
    fun `several tags walk together instead of multiplying`() {
        val graph = RecipeGraph.build(listOf(raw("x", listOf("a", "b", "c"), listOf("d", "e"))))
        assertEquals(3, graph.size)
        assertEquals(listOf("a", "d"), graph[0].inputs.map { it.id })
        assertEquals(listOf("c", "d"), graph[2].inputs.map { it.id })
    }

    @Test
    fun `recipes with nothing to say are left out`() {
        val graph = RecipeGraph.build(
            listOf(
                raw("x"), // no ingredients
                raw("", listOf("a")),
                raw("y", listOf()), // an empty slot
                raw("z", listOf("a"), type = "minecraft:smelting"),
            ),
        )
        assertEquals(1, graph.size)
        assertEquals("minecraft:smelting", graph[0].type)
    }

    @Test
    fun `too many distinct inputs and identical recipes are dropped`() {
        val many = raw("x", *Array(28) { listOf("i$it") })
        val same = raw("z", listOf("a"))
        assertEquals(1, RecipeGraph.build(listOf(many, same, same)).size)
    }
}

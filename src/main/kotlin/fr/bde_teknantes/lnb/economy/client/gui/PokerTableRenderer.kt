package fr.bde_teknantes.lnb.economy.client.gui

import com.mojang.blaze3d.vertex.PoseStack
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.poker.PokerTableBlock
import fr.bde_teknantes.lnb.economy.poker.PokerTableBlockEntity
import fr.bde_teknantes.lnb.economy.poker.PokerTableView
import fr.bde_teknantes.lnb.economy.poker.table.BlackjackCardGeometry
import fr.bde_teknantes.lnb.economy.poker.table.BlackjackCardMotion
import fr.bde_teknantes.lnb.economy.poker.table.BlackjackDealerModel
import fr.bde_teknantes.lnb.economy.poker.table.BlackjackLayout
import fr.bde_teknantes.lnb.economy.poker.table.DealerPlacement
import fr.bde_teknantes.lnb.economy.poker.table.LayerOffset
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * The poker table's dealer, standing behind it, and its cards, as MineVegas Casino's poker table draws them: the same
 * dealer model and animations, the same card flights (see `poker/table`). The round comes from [PokerTableView].
 */
class PokerTableRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<PokerTableBlockEntity> {
    private val model = BlackjackDealerModel(BlackjackDealerModel.createLayer().bakeRoot(), RenderType::entityCutout)
    private val state = BlackjackDealerModel.State()

    override fun render(table: PokerTableBlockEntity, partialTick: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        val level = table.level ?: return
        val facing = table.blockState.getValue(PokerTableBlock.FACING)
        val view = table.view ?: PokerTableView.EMPTY
        val pos = table.blockPos
        val now = level.gameTime + partialTick.toDouble()

        // The dealer stands behind the table: light him like the blocks he stands in.
        var block = LightTexture.block(light)
        var sky = LightTexture.sky(light)
        for (column in 1..2) {
            val behind = LevelRenderer.getLightColor(level, pos.offset(PokerTableBlock.rotatedOffset(facing, column - 1, -2)))
            block = maxOf(block, LightTexture.block(behind))
            sky = maxOf(sky, LightTexture.sky(behind))
        }
        val dealerLight = LightTexture.pack(block, sky)
        // Tables side by side don't breathe in step.
        val phase = Math.floorMod(pos.x * 31L + pos.y * 7L + pos.z * 17L, 480L)
        state.ticks = (now + phase).toFloat()
        view.dealerInput(now, state.play)
        model.setupAnim(state)

        pose.pushPose()
        DealerPlacement.apply(pose, facing)
        for ((texture, step) in LAYERS) {
            model.renderToBuffer(LayerOffset.of(pose, step), buffers.getBuffer(RenderType.entityCutout(texture)), dealerLight, OverlayTexture.NO_OVERLAY, -1)
        }
        pose.popPose()

        cards(view, now, facing.get2DDataValue(), pose, buffers, light)
    }

    private fun cards(view: PokerTableView, now: Double, turns: Int, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        val quads = ArrayList<BlackjackCardGeometry.Quad>()
        val cardPose = BlackjackCardMotion.Pose()
        for (card in view.cards) {
            if (BlackjackCardMotion.pose(card, view.slot(card), now, cardPose)) BlackjackCardGeometry.quads(cardPose, card.value, quads::add)
        }
        view.shuffleSince(now)?.let { since ->
            val deck = ArrayList<BlackjackCardMotion.Pose>()
            BlackjackCardMotion.shuffleDeck(since, deck)
            deck.filter { it.visible }.forEach { BlackjackCardGeometry.quads(it, -1, quads::add) }
        }
        if (quads.isEmpty()) return
        val vertices = buffers.getBuffer(RenderType.entityCutout(CARDS))
        val last = pose.last()
        for (quad in quads) {
            var nx = quad.normal[0]
            var nz = quad.normal[2]
            repeat(turns) {
                val rotated = -nz
                nz = nx
                nx = rotated
            }
            for (i in 0 until 4) {
                val p = BlackjackLayout.toBlock(quad.v[i][0], quad.v[i][1], quad.v[i][2], turns)
                vertices.addVertex(last, p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
                    .setColor(-1)
                    .setUv(quad.uv[i][0].toFloat(), quad.uv[i][1].toFloat())
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(last, nx.toFloat(), quad.normal[1].toFloat(), nz.toFloat())
            }
        }
    }

    override fun shouldRenderOffScreen(table: PokerTableBlockEntity): Boolean = true

    override fun getViewDistance(): Int = 64

    override fun getRenderBoundingBox(table: PokerTableBlockEntity): AABB =
        AABB(-4.0, -0.0625, -4.0, 5.0, 4.75, 5.0).move(Vec3.atLowerCornerOf(table.blockPos))

    private companion object {
        /** MineVegas's dealer layers, bottom up, with how far each stands proud of the one under it. The base is vanilla's villager. */
        val LAYERS: List<Pair<ResourceLocation, Int>> = listOf(
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png") to 0,
            LnbEconomy.id("textures/entity/poker_dealer/clothes.png") to 1,
            LnbEconomy.id("textures/entity/poker_dealer/outfit.png") to 2,
            LnbEconomy.id("textures/entity/poker_dealer/badge.png") to 3,
            LnbEconomy.id("textures/entity/poker_dealer/arms.png") to 0,
        )
        val CARDS: ResourceLocation = LnbEconomy.id("textures/entity/poker_cards.png")
    }
}

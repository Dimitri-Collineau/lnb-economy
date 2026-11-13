package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.chip.Chips
import fr.bde_teknantes.lnb.economy.client.ClientPoker
import fr.bde_teknantes.lnb.economy.network.PokerActionPayload
import fr.bde_teknantes.lnb.economy.network.PokerSeatView
import fr.bde_teknantes.lnb.economy.network.PokerStatePayload
import fr.bde_teknantes.lnb.economy.poker.Cards
import fr.bde_teknantes.lnb.economy.poker.HoldemRound
import fr.bde_teknantes.lnb.economy.poker.HoldemRules
import fr.bde_teknantes.lnb.economy.poker.PokerTableBlock
import fr.bde_teknantes.lnb.economy.poker.table.BlackjackLayout
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A poker table: the dealer's two cards and the board at the top, then the three seats side by side, each with its
 * player's face, cards, ante and what they chose. A free seat has a "Sit" button that antes what the box at the
 * bottom says; a seated player gets "Call" and "Fold" when the cards are out. Everyone at the table sees the same
 * thing, and so does anyone watching.
 */
class PokerScreen(val pos: BlockPos) : Screen(Component.translatable("gui.lnbeconomy.poker.title")) {
    private companion object {
        const val PANEL_WIDTH = 312
        const val PANEL_HEIGHT = 226
        const val CARD_WIDTH = 25
        const val CARD_HEIGHT = 32
        const val CARD_GAP = 2
        const val DEALER_Y = 23
        const val BOARD_Y = 58
        const val SEATS_Y = 96
        const val SEAT_WIDTH = 92
        const val SEAT_HEIGHT = 86
        const val SEAT_GAP = 6
        const val CONTROLS_Y = 188
        const val PLAY_WIDTH = 160
        const val PLAY_HEIGHT = 196

        /** Where the camera looks when the deal starts, in table pixels: the dealer's hands, over the felt. */
        const val AIM_X = 32.0
        const val AIM_Y = 18.0
        const val AIM_Z = 12.0
        const val AIM_NANOS = 6.0e8

        /**
         * MineVegas Casino's playing cards (see `LICENSES/MineVegas-Casino.txt`): 14 columns of 25 x 32, ace to king
         * then the back, and one row per suit in [Cards]' order.
         */
        val CARDS: ResourceLocation = LnbEconomy.id("textures/gui/cards.png")
        const val SHEET_WIDTH = 350
        const val SHEET_HEIGHT = 128
        const val BACK_COLUMN = 13
        const val GREEN = 0xFF78E68C.toInt()
        const val LOSS = 0xFFFF6060.toInt()
    }

    private var ante: EditBox? = null
    private var anteText = "10"
    private var shownKey = ""
    private var panelX = 0
    private var panelY = 0

    private val state: PokerStatePayload? get() = ClientPoker.state?.takeIf { it.pos == pos }
    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val me: String get() = minecraft?.player?.gameProfile?.name.orEmpty()
    private val seatsX: Int get() = panelX + (PANEL_WIDTH - 3 * SEAT_WIDTH - 2 * SEAT_GAP) / 2

    private fun phase(state: PokerStatePayload) = HoldemRound.Phase.entries.getOrElse(state.phase) { HoldemRound.Phase.WAITING }

    private fun mySeat(state: PokerStatePayload): PokerSeatView? = state.seats.firstOrNull { it?.name == me }

    private fun chips(state: PokerStatePayload): Long = minecraft?.player?.inventory?.let { Chips.valueIn(it, state.handle) } ?: 0

    /** What decides which buttons show: when it changes, the widgets are built again. */
    private fun key(state: PokerStatePayload?): String =
        if (state == null) "" else "${state.phase}|${state.seats.map { it?.name }}|${mySeat(state)?.choice}|${width}x$height"

    /**
     * Whether the player is in a round: then the screen steps aside, as MineVegas's does, into a narrow panel on the
     * right that leaves the table, its cards and its dealer in sight.
     */
    private fun playing(state: PokerStatePayload?): Boolean =
        state != null && mySeat(state) != null && phase(state).let {
            it == HoldemRound.Phase.DEALING || it == HoldemRound.Phase.DECISION || it == HoldemRound.Phase.SHOWDOWN
        }

    override fun init() {
        val state = state
        shownKey = key(state)
        ante = null
        if (playing(state)) {
            initPlay(state!!)
            return
        }
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        val controlsY = panelY + CONTROLS_Y
        addRenderableWidget(
            CasinoButton.secondary(CommonComponents.GUI_DONE) { onClose() }.bounds(panelX + PANEL_WIDTH - 20 - 60, controlsY, 60, 18).build(),
        )
        if (state == null) return
        val phase = phase(state)
        val mine = mySeat(state)
        val joining = phase == HoldemRound.Phase.WAITING || phase == HoldemRound.Phase.BETTING

        if (joining && mine == null) {
            val box = CasinoUi.editBox(font, panelX + 20, controlsY, 70, Component.translatable("gui.lnbeconomy.poker.ante"), 9)
            box.setFilter { text -> text.all(Char::isDigit) }
            box.value = anteText
            box.setResponder { anteText = it }
            ante = addRenderableWidget(box)
            state.seats.forEachIndexed { index, seat ->
                if (seat != null) return@forEachIndexed
                val x = seatsX + index * (SEAT_WIDTH + SEAT_GAP)
                addRenderableWidget(
                    CasinoButton.primary(Component.translatable("gui.lnbeconomy.poker.sit")) {
                        val value = anteText.toLongOrNull() ?: 0
                        PacketDistributor.sendToServer(PokerActionPayload(pos, PokerActionPayload.SIT, index, value))
                    }.bounds(x + 14, panelY + SEATS_Y + 36, SEAT_WIDTH - 28, 18).build(),
                )
            }
        }
        if (phase == HoldemRound.Phase.DECISION && mine != null && mine.choice == HoldemRound.Choice.NONE.ordinal) {
            val cost = mine.ante * HoldemRules.CALL_ANTES
            val call: Button = addRenderableWidget(
                CasinoButton.primary(Component.translatable("gui.lnbeconomy.poker.call", Lnbit.compact(cost, language))) {
                    PacketDistributor.sendToServer(PokerActionPayload(pos, PokerActionPayload.CALL))
                }.bounds(panelX + 20, controlsY, 110, 18).build(),
            )
            call.active = chips(state) >= cost
            addRenderableWidget(
                CasinoButton.secondary(Component.translatable("gui.lnbeconomy.poker.fold")) {
                    PacketDistributor.sendToServer(PokerActionPayload(pos, PokerActionPayload.FOLD))
                }.bounds(panelX + 134, controlsY, 70, 18).build(),
            )
        }
    }

    // While playing: a side panel, and the camera turned to the dealer

    private fun initPlay(state: PokerStatePayload) {
        panelX = width - PLAY_WIDTH - 8
        panelY = (height - PLAY_HEIGHT) / 2
        val mine = mySeat(state) ?: return
        val buttonsY = panelY + PLAY_HEIGHT - 28
        val inner = PLAY_WIDTH - 20
        if (phase(state) == HoldemRound.Phase.DECISION && mine.choice == HoldemRound.Choice.NONE.ordinal) {
            val cost = mine.ante * HoldemRules.CALL_ANTES
            val half = (inner - 2) / 2
            val call: Button = addRenderableWidget(
                CasinoButton.primary(Component.translatable("gui.lnbeconomy.poker.call", Lnbit.compact(cost, language))) {
                    PacketDistributor.sendToServer(PokerActionPayload(pos, PokerActionPayload.CALL))
                }.bounds(panelX + 10, buttonsY, half, 18).build(),
            )
            call.active = chips(state) >= cost
            addRenderableWidget(
                CasinoButton.secondary(Component.translatable("gui.lnbeconomy.poker.fold")) {
                    PacketDistributor.sendToServer(PokerActionPayload(pos, PokerActionPayload.FOLD))
                }.bounds(panelX + 12 + half, buttonsY, inner - half - 2, 18).build(),
            )
        } else {
            addRenderableWidget(
                CasinoButton.secondary(CommonComponents.GUI_DONE) { onClose() }.bounds(panelX + 10, buttonsY, inner, 18).build(),
            )
        }
    }

    /** The phase the screen last saw: the camera turns when it becomes the deal. */
    private var lastPhase: HoldemRound.Phase? = null
    private var aiming = false
    private var aimStart = 0L
    private var aimYaw0 = 0f
    private var aimPitch0 = 0f
    private var aimYawTurn = 0f
    private var aimPitchTurn = 0f

    /** Turns the player toward the dealer over a little more than half a second, as MineVegas's tables do. */
    private fun startAim() {
        val player = minecraft?.player ?: return
        val level = minecraft?.level ?: return
        val blockState = level.getBlockState(pos)
        if (blockState.block !is PokerTableBlock) return
        val turns = blockState.getValue(PokerTableBlock.FACING).get2DDataValue()
        val target = BlackjackLayout.toBlock(AIM_X, AIM_Y, AIM_Z, turns)
        val eye = player.getEyePosition(1f)
        val dx = pos.x + target[0] - eye.x
        val dy = pos.y + target[1] - eye.y
        val dz = pos.z + target[2] - eye.z
        val flat = Math.sqrt(dx * dx + dz * dz)
        if (flat < 0.25) return
        val yaw = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-Math.atan2(dy, flat)).toFloat().coerceIn(-60f, 60f)
        aimYaw0 = player.yRot
        aimPitch0 = player.xRot
        aimYawTurn = Mth.wrapDegrees(yaw - aimYaw0)
        aimPitchTurn = pitch - aimPitch0
        aimStart = System.nanoTime()
        aiming = true
    }

    private fun stepAim() {
        if (!aiming) return
        val player = minecraft?.player ?: return run { aiming = false }
        val progress = ((System.nanoTime() - aimStart) / AIM_NANOS).toFloat().coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val yaw = aimYaw0 + aimYawTurn * eased - player.yRot
        val pitch = aimPitch0 + aimPitchTurn * eased - player.xRot
        player.yRot += yaw
        player.yRotO += yaw
        player.xRot += pitch
        player.xRotO += pitch
        if (progress >= 1f) aiming = false
    }

    private fun renderPlay(graphics: GuiGraphics, state: PokerStatePayload) {
        val x = panelX + 10
        val inner = PLAY_WIDTH - 20
        CasinoUi.title(graphics, font, Component.literal(CasinoUi.fit(font, state.casinoName, inner)), panelX + PLAY_WIDTH / 2, panelY + 9)
        val phase = phase(state)
        val seconds = (ClientPoker.ticksLeft() + 19) / 20
        val status = when (phase) {
            HoldemRound.Phase.DEALING -> Component.translatable("gui.lnbeconomy.poker.phase.dealing")
            HoldemRound.Phase.DECISION -> Component.translatable("gui.lnbeconomy.poker.phase.decision", seconds)
            else -> Component.translatable("gui.lnbeconomy.poker.phase.showdown")
        }
        graphics.drawCenteredString(font, status, panelX + PLAY_WIDTH / 2, panelY + 24, CasinoUi.GOLD_TEXT)
        if (phase == HoldemRound.Phase.DECISION) {
            // What is left of the time to decide.
            val left = ClientPoker.ticksLeft().toFloat() / HoldemRound.DECISION_TICKS
            graphics.fill(x, panelY + 35, x + inner, panelY + 37, 0x60000000)
            graphics.fill(x, panelY + 35, x + (inner * left).toInt(), panelY + 37, CasinoUi.GOLD_TEXT)
        }

        // The board and the dealer, small: the table shows them big.
        val board = (0 until 5).map { state.board.getOrNull(it) }
        miniCards(graphics, board, x, panelY + 42)
        val dealerLabel = if (state.dealerHand >= 0) Component.translatable("gui.lnbeconomy.poker.dealer.hand", hand(state.dealerHand))
        else Component.translatable("gui.lnbeconomy.poker.dealer")
        graphics.drawString(font, CasinoUi.fit(font, dealerLabel.string, inner - 30), x, panelY + 66, CasinoUi.DIM, true)
        miniCards(graphics, state.dealer.ifEmpty { listOf(null, null) }, x + inner - 25, panelY + 62)

        state.seats.forEachIndexed { index, seat ->
            val y = panelY + 84 + index * 24
            graphics.blitSprite(CasinoUi.PANEL_INSET, x - 2, y - 2, inner + 4, 22)
            if (seat == null) {
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.poker.seat.empty"), x + 3, y + 5, CasinoUi.GREY, true)
                return@forEachIndexed
            }
            Heads.draw(graphics, seat.name, x + 2, y + 3, 8)
            graphics.drawString(font, CasinoUi.fit(font, seat.name, 70), x + 13, y + 1, if (seat.name == me) CasinoUi.GOLD_TEXT else CasinoUi.IVORY, true)
            val (line, color) = seatLine(seat, phase)
            graphics.drawString(font, CasinoUi.fit(font, line.string, inner - 16), x + 13, y + 10, color, true)
            miniCards(graphics, seat.cards.ifEmpty { listOf(null, null) }, x + inner - 25, y + 1)
        }
    }

    /** A seat's state on one line: thinking, called, folded, or its result. */
    private fun seatLine(seat: PokerSeatView, phase: HoldemRound.Phase): Pair<Component, Int> {
        val result = HoldemRules.Result.entries.getOrNull(seat.result)
        return when {
            result == HoldemRules.Result.FOLD -> Component.translatable("gui.lnbeconomy.poker.choice.fold") to CasinoUi.GREY
            result == HoldemRules.Result.LOSE -> Component.translatable("gui.lnbeconomy.poker.result.lose", hand(seat.hand)) to LOSS
            result == HoldemRules.Result.PUSH -> Component.translatable("gui.lnbeconomy.poker.result.push", Lnbit.compact(seat.payout, language)) to CasinoUi.IVORY
            result != null -> Component.translatable("gui.lnbeconomy.poker.result.win", hand(seat.hand), Lnbit.compact(seat.payout, language)) to GREEN
            seat.choice == HoldemRound.Choice.CALL.ordinal -> Component.translatable("gui.lnbeconomy.poker.choice.call") to CasinoUi.GOLD_TEXT
            seat.choice == HoldemRound.Choice.FOLD.ordinal -> Component.translatable("gui.lnbeconomy.poker.choice.fold") to CasinoUi.GREY
            phase == HoldemRound.Phase.DECISION -> Component.translatable("gui.lnbeconomy.poker.choice.thinking") to CasinoUi.DIM
            else -> Component.translatable("gui.lnbeconomy.poker.staked", Lnbit.compact(seat.ante, language)) to CasinoUi.DIM
        }
    }

    /** Cards at half size, side by side from [x]. */
    private fun miniCards(graphics: GuiGraphics, cards: List<Int?>, x: Int, y: Int) {
        graphics.pose().pushPose()
        graphics.pose().translate(x.toFloat(), y.toFloat(), 0f)
        graphics.pose().scale(0.5f, 0.5f, 1f)
        cards.forEachIndexed { i, card -> card(graphics, card, i * (CARD_WIDTH + CARD_GAP), 0) }
        graphics.pose().popPose()
    }

    override fun tick() {
        val state = state
        val phase = state?.let(::phase)
        if (phase == HoldemRound.Phase.DEALING && lastPhase != null && lastPhase != phase && mySeat(state) != null) startAim()
        lastPhase = phase
        if (key(state) != shownKey) rebuildWidgets()
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (playing(state)) {
            // No veil over the world: the table is the show.
            CasinoUi.panel(graphics, panelX, panelY, PLAY_WIDTH, PLAY_HEIGHT)
            return
        }
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        for (index in 0 until HoldemRound.SEATS) {
            graphics.blitSprite(CasinoUi.PANEL_INSET, seatsX + index * (SEAT_WIDTH + SEAT_GAP), panelY + SEATS_Y, SEAT_WIDTH, SEAT_HEIGHT)
        }
        ante?.let { CasinoUi.field(graphics, it) }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        stepAim()
        super.render(graphics, mouseX, mouseY, partialTick)
        val state = state
        if (playing(state)) return renderPlay(graphics, state!!)
        val title = if (state == null) title else Component.translatable("gui.lnbeconomy.poker.title.at", state.casinoName)
        CasinoUi.title(graphics, font, Component.literal(CasinoUi.fit(font, title.string, PANEL_WIDTH - 30)), width / 2, panelY + 10)
        if (state == null) return
        val phase = phase(state)
        val center = panelX + PANEL_WIDTH / 2

        // The dealer: two cards, with his hand's name once they are turned.
        cards(graphics, state.dealer.ifEmpty { listOf(null, null) }, center, panelY + DEALER_Y)
        val dealerLabel = if (state.dealerHand >= 0) Component.translatable("gui.lnbeconomy.poker.dealer.hand", hand(state.dealerHand))
        else Component.translatable("gui.lnbeconomy.poker.dealer")
        graphics.drawString(font, dealerLabel, panelX + 20, panelY + DEALER_Y + 10, CasinoUi.DIM, true)
        // The board: five places, filled as the cards come.
        cards(graphics, (0 until 5).map { state.board.getOrNull(it) }, center, panelY + BOARD_Y)

        state.seats.forEachIndexed { index, seat -> seat(graphics, state, phase, index, seat) }

        val seconds = (ClientPoker.ticksLeft() + 19) / 20
        val status = when (phase) {
            HoldemRound.Phase.WAITING -> Component.translatable("gui.lnbeconomy.poker.phase.waiting")
            HoldemRound.Phase.BETTING -> Component.translatable("gui.lnbeconomy.poker.phase.betting", seconds)
            HoldemRound.Phase.DEALING -> Component.translatable("gui.lnbeconomy.poker.phase.dealing")
            HoldemRound.Phase.DECISION -> Component.translatable("gui.lnbeconomy.poker.phase.decision", seconds)
            HoldemRound.Phase.SHOWDOWN -> Component.translatable("gui.lnbeconomy.poker.phase.showdown")
        }
        graphics.drawString(font, status, panelX + PANEL_WIDTH - 20 - font.width(status), panelY + DEALER_Y + 10, CasinoUi.GOLD_TEXT, true)

        if (ante != null) {
            val carried = Component.translatable("gui.lnbeconomy.poker.chips", Lnbit.compact(chips(state), language))
            graphics.drawString(font, carried, panelX + 96, panelY + CONTROLS_Y + 5, CasinoUi.DIM, true)
        }
        val rules = Component.translatable(if (state.maxBet > 0) "gui.lnbeconomy.poker.rules.max" else "gui.lnbeconomy.poker.rules", Lnbit.compact(state.maxBet, language))
        graphics.drawCenteredString(font, rules, center, panelY + CONTROLS_Y + 23, CasinoUi.DIM)
    }

    private fun seat(graphics: GuiGraphics, state: PokerStatePayload, phase: HoldemRound.Phase, index: Int, seat: PokerSeatView?) {
        val x = seatsX + index * (SEAT_WIDTH + SEAT_GAP)
        val y = panelY + SEATS_Y
        val middle = x + SEAT_WIDTH / 2
        if (seat == null) {
            val free = Component.translatable(if (phase == HoldemRound.Phase.WAITING || phase == HoldemRound.Phase.BETTING) "gui.lnbeconomy.poker.seat.free" else "gui.lnbeconomy.poker.seat.empty")
            graphics.drawCenteredString(font, free, middle, y + 14, CasinoUi.GREY)
            return
        }
        Heads.draw(graphics, seat.name, x + 6, y + 5, 10)
        graphics.drawString(font, CasinoUi.fit(font, seat.name, SEAT_WIDTH - 26), x + 20, y + 6, if (seat.name == me) CasinoUi.GOLD_TEXT else CasinoUi.IVORY, true)
        cards(graphics, seat.cards.ifEmpty { listOf(null, null) }, middle, y + 20)

        val staked = if (seat.choice == HoldemRound.Choice.CALL.ordinal) seat.ante * (1 + HoldemRules.CALL_ANTES) else seat.ante
        val stake = Component.translatable("gui.lnbeconomy.poker.staked", Lnbit.compact(staked, language))
        graphics.drawCenteredString(font, stake, middle, y + 56, CasinoUi.DIM)

        val result = HoldemRules.Result.entries.getOrNull(seat.result)
        val (line, color) = when {
            result == HoldemRules.Result.FOLD -> Component.translatable("gui.lnbeconomy.poker.choice.fold") to CasinoUi.GREY
            result == HoldemRules.Result.LOSE -> Component.translatable("gui.lnbeconomy.poker.result.lose", hand(seat.hand)) to LOSS
            result == HoldemRules.Result.PUSH -> Component.translatable("gui.lnbeconomy.poker.result.push", Lnbit.compact(seat.payout, language)) to CasinoUi.IVORY
            result != null -> Component.translatable("gui.lnbeconomy.poker.result.win", hand(seat.hand), Lnbit.compact(seat.payout, language)) to GREEN
            seat.choice == HoldemRound.Choice.CALL.ordinal -> Component.translatable("gui.lnbeconomy.poker.choice.call") to CasinoUi.GOLD_TEXT
            seat.choice == HoldemRound.Choice.FOLD.ordinal -> Component.translatable("gui.lnbeconomy.poker.choice.fold") to CasinoUi.GREY
            phase == HoldemRound.Phase.DECISION -> Component.translatable("gui.lnbeconomy.poker.choice.thinking") to CasinoUi.DIM
            else -> Component.translatable("gui.lnbeconomy.poker.choice.ready") to CasinoUi.DIM
        }
        graphics.drawCenteredString(font, CasinoUi.fit(font, line.string, SEAT_WIDTH - 6), middle, y + 69, color)
    }

    private fun hand(category: Int): Component = Component.translatable("gui.lnbeconomy.poker.hand.${category.coerceIn(0, 8)}")

    /** A row of cards centered on [centerX]. Null is an empty place, [Cards.HIDDEN] a card face down. */
    private fun cards(graphics: GuiGraphics, cards: List<Int?>, centerX: Int, y: Int) {
        val total = cards.size * CARD_WIDTH + (cards.size - 1) * CARD_GAP
        cards.forEachIndexed { i, card -> card(graphics, card, centerX - total / 2 + i * (CARD_WIDTH + CARD_GAP), y) }
    }

    private fun card(graphics: GuiGraphics, card: Int?, x: Int, y: Int) {
        if (card == null) {
            graphics.renderOutline(x, y, CARD_WIDTH, CARD_HEIGHT, 0x60F0E8D0)
            return
        }
        val shown = card in 0 until Cards.COUNT
        // The sheet starts at the ace; ranks here start at the two.
        val column = if (shown) (Cards.rank(card) + 1) % Cards.RANKS else BACK_COLUMN
        val row = if (shown) Cards.suit(card) else 0
        graphics.blit(CARDS, x, y, (column * CARD_WIDTH).toFloat(), (row * CARD_HEIGHT).toFloat(), CARD_WIDTH, CARD_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT)
    }

    override fun onClose() {
        PacketDistributor.sendToServer(PokerActionPayload(pos, PokerActionPayload.CLOSE))
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}

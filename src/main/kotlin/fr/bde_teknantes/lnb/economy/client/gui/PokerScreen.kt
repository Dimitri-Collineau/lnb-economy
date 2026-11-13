package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.chip.Chips
import fr.bde_teknantes.lnb.economy.client.ClientPoker
import fr.bde_teknantes.lnb.economy.network.PokerActionPayload
import fr.bde_teknantes.lnb.economy.network.PokerSeatView
import fr.bde_teknantes.lnb.economy.network.PokerStatePayload
import fr.bde_teknantes.lnb.economy.poker.Cards
import fr.bde_teknantes.lnb.economy.poker.HoldemRound
import fr.bde_teknantes.lnb.economy.poker.HoldemRules
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
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
        const val CARD_WIDTH = 20
        const val CARD_HEIGHT = 28
        const val CARD_GAP = 3
        const val DEALER_Y = 24
        const val BOARD_Y = 60
        const val SEATS_Y = 98
        const val SEAT_WIDTH = 92
        const val SEAT_HEIGHT = 82
        const val SEAT_GAP = 6
        const val CONTROLS_Y = 188
        const val SUITS = "♠♥♦♣"
        const val CARD_FACE = 0xFFF4EEDC.toInt()
        const val CARD_EDGE = 0xFF2A2018.toInt()
        const val CARD_BACK = 0xFF8E1F2B.toInt()
        const val CARD_BACK_INNER = 0xFFB12C3A.toInt()
        const val BLACK = 0xFF1C1C1C.toInt()
        const val RED = 0xFFC22A2A.toInt()
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
        if (state == null) "" else "${state.phase}|${state.seats.map { it?.name }}|${mySeat(state)?.choice}"

    override fun init() {
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        val state = state
        shownKey = key(state)
        ante = null
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
                    }.bounds(x + 14, panelY + SEATS_Y + 34, SEAT_WIDTH - 28, 18).build(),
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

    override fun tick() {
        if (key(state) != shownKey) rebuildWidgets()
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        for (index in 0 until HoldemRound.SEATS) {
            graphics.blitSprite(CasinoUi.PANEL_INSET, seatsX + index * (SEAT_WIDTH + SEAT_GAP), panelY + SEATS_Y, SEAT_WIDTH, SEAT_HEIGHT)
        }
        ante?.let { CasinoUi.field(graphics, it) }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val state = state
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
        cards(graphics, seat.cards.ifEmpty { listOf(null, null) }, middle, y + 19)

        val staked = if (seat.choice == HoldemRound.Choice.CALL.ordinal) seat.ante * (1 + HoldemRules.CALL_ANTES) else seat.ante
        val stake = Component.translatable("gui.lnbeconomy.poker.staked", Lnbit.compact(staked, language))
        graphics.drawCenteredString(font, stake, middle, y + 51, CasinoUi.DIM)

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
        graphics.drawCenteredString(font, CasinoUi.fit(font, line.string, SEAT_WIDTH - 6), middle, y + 65, color)
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
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, CARD_EDGE)
        if (card == Cards.HIDDEN || card !in 0 until Cards.COUNT) {
            graphics.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, CARD_BACK)
            graphics.renderOutline(x + 3, y + 3, CARD_WIDTH - 6, CARD_HEIGHT - 6, CasinoUi.GOLD_TEXT)
            graphics.fill(x + 5, y + 5, x + CARD_WIDTH - 5, y + CARD_HEIGHT - 5, CARD_BACK_INNER)
            return
        }
        graphics.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, CARD_FACE)
        val suit = Cards.suit(card)
        val color = if (suit == 1 || suit == 2) RED else BLACK
        graphics.drawString(font, Cards.rankLabel(card), x + 3, y + 3, color, false)
        val glyph = SUITS[suit].toString()
        graphics.drawString(font, glyph, x + CARD_WIDTH - 3 - font.width(glyph), y + CARD_HEIGHT - 11, color, false)
    }

    override fun onClose() {
        PacketDistributor.sendToServer(PokerActionPayload(pos, PokerActionPayload.CLOSE))
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}

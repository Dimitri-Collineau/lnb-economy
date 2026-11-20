package fr.bde_teknantes.lnb.economy.shop

import net.minecraft.nbt.CompoundTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShopSettingsTagTest {
    private fun oldTag() = CompoundTag().apply {
        putInt("lotSize", 4)
        putLong("sellPrice", 12)
        putLong("buyPrice", 5)
        putInt("buyCap", 64)
    }

    @Test
    fun `prices saved before cents are whole LNBits and become cents`() {
        val settings = ShopSettings.fromTag(oldTag())
        assertEquals(1200, settings.sellPrice)
        assertEquals(500, settings.buyPrice)
    }

    @Test
    fun `a converted shop is not converted again`() {
        val saved = ShopSettings.fromTag(oldTag()).toTag()
        val again = ShopSettings.fromTag(saved)
        assertEquals(1200, again.sellPrice)
        assertEquals(500, again.buyPrice)
    }

    @Test
    fun `no buy-back price stays none`() {
        val tag = oldTag().apply { remove("buyPrice") }
        assertNull(ShopSettings.fromTag(tag).buyPrice)
    }
}

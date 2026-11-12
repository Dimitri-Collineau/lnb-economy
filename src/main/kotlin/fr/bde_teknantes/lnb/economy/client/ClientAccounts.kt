package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.network.AccountFeedbackPayload
import fr.bde_teknantes.lnb.economy.network.AccountMembersPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

/** What the account screens last heard from the server: a shared account's members, and how the last request ended. */
object ClientAccounts {
    var members: AccountMembersPayload? = null
        private set

    /** The last answer to a request made from a screen: whether it worked, and the message to show. */
    var feedback: Pair<Boolean, Component>? = null
        private set

    /** Bumped on every answer, so open screens know when to rebuild. */
    var revision = 0
        private set

    fun update(payload: AccountMembersPayload) {
        members = payload
        revision++
    }

    fun update(payload: AccountFeedbackPayload) {
        feedback = payload.ok to Component.translatable(payload.key, *payload.args.toTypedArray())
        revision++
        if (!payload.ok) {
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.5f))
        }
    }

    /** A screen that opens starts without the previous one's answer. */
    fun clearFeedback() {
        feedback = null
    }

    fun clear() {
        members = null
        feedback = null
        revision++
    }
}

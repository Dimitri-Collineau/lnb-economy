package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.network.AuctionListingsPayload
import fr.bde_teknantes.lnb.economy.network.AuctionMailboxPayload
import fr.bde_teknantes.lnb.economy.network.AuctionRequestPayload

/** What the server last sent for each tab of the auction house. [revision] changes with each update. */
object ClientAuction {
    var browse: AuctionListingsPayload? = null
        private set
    var mine: AuctionListingsPayload? = null
        private set
    var mailbox: AuctionMailboxPayload? = null
        private set
    var revision = 0
        private set

    fun update(payload: AuctionListingsPayload) {
        if (payload.tab == AuctionRequestPayload.MINE) mine = payload else browse = payload
        revision++
    }

    fun update(payload: AuctionMailboxPayload) {
        mailbox = payload
        revision++
    }

    fun clear() {
        browse = null
        mine = null
        mailbox = null
        revision++
    }
}

package fr.bde_teknantes.lnb.economy.api

import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import java.net.http.HttpRequest

/**
 * Every economy call must say `X-Economy-Units: cents`: without it the site answers in whole LNBits (legacy mode) and
 * the amounts would be wrong by a factor of 100. LNB Warden's `ApiClient.send` carries [HEADERS] (from Warden 0.4.0).
 */
object EconomyHttp {
    const val UNITS_HEADER = "X-Economy-Units"
    const val UNITS = "cents"

    val HEADERS = mapOf(UNITS_HEADER to UNITS)

    /** A request for the event stream, which doesn't go through `ApiClient.send`, with the units header. */
    fun request(baseUrl: String, path: String, token: String?): HttpRequest.Builder =
        ApiClient.request(baseUrl, path, token).header(UNITS_HEADER, UNITS)
}

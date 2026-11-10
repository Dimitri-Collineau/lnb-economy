package fr.bde_teknantes.lnb.economy.api

import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.ApiHealth
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * The `/api/economy/server` routes of LNB's site, called with the server's token from LNB Warden's config
 * (`api.url` and `api.token` in `lnbwarden-server.toml`), so the secret lives in one place.
 *
 * Every call is asynchronous and never throws: callers hop back to the server thread before touching the game.
 */
object EconomyApi {
    private const val BASE = "/api/economy/server"

    /** The site's base URL and the server's token, or null when they aren't set. Replaced by the integration tests. */
    @Volatile
    var connection: () -> Pair<String, String>? = {
        if (ServerConfig.isApiConfigured) ServerConfig.API_URL.get() to ServerConfig.API_TOKEN.get() else null
    }

    fun player(uuid: UUID): CompletableFuture<ApiResult<ServerPlayer>> =
        call("economy player lookup", "GET", "$BASE/players/$uuid")

    /** [account] is an account ID or a handle; null pays with the personal account. */
    fun setDefaultAccount(uuid: UUID, account: String?): CompletableFuture<ApiResult<ServerPlayer>> =
        call("economy default account", "PUT", "$BASE/players/$uuid/default-account", DefaultAccountBody(account))

    /** [handle] with or without its `#`. */
    fun account(handle: String): CompletableFuture<ApiResult<ServerAccount>> =
        call("economy account lookup", "GET", "$BASE/accounts/${encode(Handles.bare(handle))}")

    fun transfer(body: TransferBody): CompletableFuture<ApiResult<TransferResult>> =
        call("economy transfer", "POST", "$BASE/transfers", body)

    private fun encode(segment: String): String = URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")

    private inline fun <reified T> call(what: String, method: String, path: String, body: Any? = null): CompletableFuture<ApiResult<T>> {
        val (url, token) = connection() ?: run {
            val result = ApiResult.Network(IllegalStateException("api.url and api.token aren't set in lnbwarden-server.toml"))
            ApiHealth.record(what, result)
            return CompletableFuture.completedFuture(result)
        }
        // Plain HTTP is allowed: the server's admin chose api.url, and LNB Warden warns about it at startup.
        return ApiClient.send<T>(url, method, path, token, body, requireSecure = false)
            .thenApply { result -> result.also { ApiHealth.record(what, it) } }
    }
}

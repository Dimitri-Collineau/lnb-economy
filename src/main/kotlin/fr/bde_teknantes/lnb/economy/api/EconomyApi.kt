package fr.bde_teknantes.lnb.economy.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
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

    /**
     * [account] is an account ID or a handle. The site also takes `null` for the personal account, but Gson leaves null
     * fields out and the site refuses a body without `account`: pass the personal account's ID instead.
     */
    fun setDefaultAccount(uuid: UUID, account: String): CompletableFuture<ApiResult<ServerPlayer>> =
        call("economy default account", "PUT", "$BASE/players/$uuid/default-account", DefaultAccountBody(account))

    /** [handle] with or without its `#`. */
    fun account(handle: String): CompletableFuture<ApiResult<ServerAccount>> =
        call("economy account lookup", "GET", "$BASE/accounts/${encode(Handles.bare(handle))}")

    fun transfer(body: TransferBody): CompletableFuture<ApiResult<TransferResult>> =
        call("economy transfer", "POST", "$BASE/transfers", body)

    fun createShop(body: ShopSettingsBody): CompletableFuture<ApiResult<ServerShop>> =
        call("economy shop save", "POST", "$BASE/shops", body)

    fun updateShop(id: String, body: ShopSettingsBody): CompletableFuture<ApiResult<ServerShop>> =
        call("economy shop save", "PUT", "$BASE/shops/${encode(id)}", body)

    fun shop(id: String): CompletableFuture<ApiResult<ServerShop>> =
        call("economy shop lookup", "GET", "$BASE/shops/${encode(id)}")

    /**
     * A POST to [route] (under `/api/economy/server`, such as `/transfers`), with an answer left as JSON. The journal
     * sends every money move through here, whatever its route.
     */
    fun post(route: String, body: JsonObject): CompletableFuture<ApiResult<JsonElement>> =
        call("economy ${route.trimStart('/').substringBefore('/')}", "POST", BASE + route, body)

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

package fr.bde_teknantes.lnb.economy.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.account.SharedAccounts
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.io.BufferedReader
import java.io.IOException
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * Keeps the site's `GET /api/economy/server/events` open, on its own thread, as LNB Warden does with its own stream:
 * an account opened or an invitation sent on the site reaches the game within a second, instead of at the next
 * refresh (`cache.refreshMinutes`), which still runs in case the stream is down.
 *
 * - `accounts` `{ uuids }`: those players' accounts and invitations are read again.
 * - `invitation` `{ uuids, id, account: { handle, name }, invitedBy }`: the invited player sees it at once.
 */
object EconomyEvents {
    private const val PATH = "/api/economy/server/events"
    private const val MAX_BACKOFF_MS = 30_000L

    /** The site pings after 25 quiet seconds: past this, the connection is presumed dead. */
    private const val SILENCE_MS = 75_000L

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    private var reader: BufferedReader? = null

    @Volatile
    private var lastEventId: String? = null

    @Volatile
    private var lastDataAt = 0L

    @Volatile
    private var connected = false
    private var backoffMs = 1_000L

    fun onServerStarted(event: ServerStartedEvent) {
        if (running) return
        running = true
        val server = event.server
        thread = Thread({ run(server) }, "LNB Economy events").apply {
            isDaemon = true
            start()
        }
    }

    fun onServerStopping(event: ServerStoppingEvent) {
        running = false
        thread?.interrupt()
        thread = null
        closeReader()
    }

    /** Drops a connection the site stopped pinging, so the thread reconnects. */
    fun onServerTick(event: ServerTickEvent.Post) {
        if (connected && System.currentTimeMillis() - lastDataAt > SILENCE_MS) closeReader()
    }

    private fun run(server: MinecraftServer) {
        var lastReason: String? = null
        while (running) {
            val reason = try {
                if (EconomyApi.connection() == null) "api.url and api.token aren't set" else {
                    listen(server)
                    "the stream ended"
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            connected = false
            if (!running) break
            // Once per change, not again for each failed reconnection.
            if (reason != lastReason) LnbEconomy.LOGGER.warn("LNB's economy event stream is disconnected ({}), reconnecting", reason)
            lastReason = reason
            try {
                Thread.sleep(backoffMs)
            } catch (e: InterruptedException) {
                break
            }
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun listen(server: MinecraftServer) {
        val (url, token) = EconomyApi.connection() ?: return
        val request = EconomyHttp.request(url, PATH, token)
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(30))
            .apply { lastEventId?.let { header("Last-Event-ID", it) } }
            .GET()
            .build()
        val response = ApiClient.HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("HTTP ${response.statusCode()}")
        }
        lastDataAt = System.currentTimeMillis()
        backoffMs = 1_000L
        connected = true
        LnbEconomy.LOGGER.info("Connected to LNB's economy event stream")
        // Changes made while the stream was down are lost: read the players' accounts again.
        server.execute { server.playerList.players.forEach { PlayerAccounts.refresh(server, it.uuid, force = false) } }

        response.body().bufferedReader().use { reader ->
            this.reader = reader
            var type = "message"
            var id: String? = null
            val data = StringBuilder()
            while (running) {
                val line = reader.readLine() ?: break
                lastDataAt = System.currentTimeMillis()
                when {
                    line.isEmpty() -> {
                        if (id != null) lastEventId = id
                        if (data.isNotEmpty()) dispatch(server, type, data.toString().trimEnd('\n'))
                        type = "message"
                        id = null
                        data.clear()
                    }
                    line.startsWith(":") -> Unit
                    else -> {
                        val value = line.substringAfter(':', "").removePrefix(" ")
                        when (line.substringBefore(':')) {
                            "event" -> type = value
                            "data" -> data.append(value).append('\n')
                            "id" -> id = value
                        }
                    }
                }
            }
        }
        reader = null
    }

    private fun dispatch(server: MinecraftServer, type: String, data: String) {
        if (type == "ping") return
        val body = try {
            JsonParser.parseString(data) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return LnbEconomy.LOGGER.warn("Ignoring a malformed LNB economy {} event", type)
        val uuids = body.getAsJsonArray("uuids")?.mapNotNull { runCatching { UUID.fromString(it.asString) }.getOrNull() }.orEmpty()
        when (type) {
            "accounts" -> server.execute {
                for (uuid in uuids) if (server.playerList.getPlayer(uuid) != null) PlayerAccounts.refresh(server, uuid)
            }
            "invitation" -> {
                fun text(obj: JsonObject?, field: String) = obj?.get(field)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val id = text(body, "id")
                val account = body.getAsJsonObject("account")
                if (id.isEmpty()) return
                server.execute {
                    for (uuid in uuids) {
                        val player = server.playerList.getPlayer(uuid) ?: continue
                        SharedAccounts.notifyInvitation(player, id, text(account, "name"), text(account, "handle"), text(body, "invitedBy"))
                    }
                }
            }
            else -> LnbEconomy.LOGGER.debug("Ignoring LNB economy event {}", type)
        }
    }

    private fun closeReader() {
        try {
            reader?.close()
        } catch (_: IOException) {
        }
    }
}

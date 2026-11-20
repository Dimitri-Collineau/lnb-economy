package fr.bde_teknantes.lnb.economy.api

import com.sun.net.httpserver.HttpServer
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import java.net.InetSocketAddress
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EconomyHttpTest {
    private lateinit var server: HttpServer
    private val seen = mutableListOf<Triple<String, String?, String?>>()

    private val url get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen += Triple(exchange.requestURI.path, exchange.requestHeaders.getFirst(EconomyHttp.UNITS_HEADER), exchange.requestHeaders.getFirst("Authorization"))
            val body = """{"balance":123455}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private data class Balance(val balance: Long)

    @Test
    fun `every call says it counts in cents`() {
        val result = ApiClient.send<Balance>(url, "GET", "/api/economy/server/players/x", "secret", headers = EconomyHttp.HEADERS).get(5, TimeUnit.SECONDS)
        assertEquals(123_455L, (result as ApiResult.Ok).body.balance)
        assertEquals(Triple("/api/economy/server/players/x", "cents", "Bearer secret"), seen.single())
    }

    @Test
    fun `the event stream request carries the header too`() {
        val request = EconomyHttp.request(url, "/api/economy/server/events", "secret").GET().build()
        assertEquals("cents", request.headers().firstValue(EconomyHttp.UNITS_HEADER).orElse(null))
        fr.bde_teknantes.lnb.warden.common.api.ApiClient.HTTP.send(request, HttpResponse.BodyHandlers.discarding())
        assertEquals("cents", seen.single().second)
    }
}

package tech.yaxi.routex.settlement.internal

import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.RoutexErrorDispatcher
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.models.TraceId
import tech.yaxi.routex.settlement.RoutexClientError
import tech.yaxi.routex.settlement.SettleResult
import tech.yaxi.routex.settlement.Settlement
import tech.yaxi.routex.settlement.SystemVersionEntry
import tech.yaxi.routex.settlement.generated.ROUTEX_CLIENT_VERSION
import tech.yaxi.routex.transport.HttpMethod
import tech.yaxi.routex.transport.HttpRequest

private const val MEDIA_TYPE = "application/vnd.yaxi.v5"
private const val HEADER_CLIENT_VERSION = "yaxi-client-version"
private const val HEADER_TICKET_ID = "yaxi-ticket-id"
private const val HEADER_TICKET = "yaxi-ticket"
private const val HEADER_SESSION_ID = "yaxi-session-id"
private const val HEADER_REDIRECT_URI = "yaxi-redirect-uri"
private const val HEADER_TRACE_ID = "yaxi-trace-id"
private const val HTTP_ERROR_BAD_REQUEST = 400

private val USER_AGENT = "RoutexClient/$ROUTEX_CLIENT_VERSION ($routexClientPlatform)"

/**
 * Shared sealed-HTTP plumbing that backs both `RoutexClient` and `RoutexRefreshClient`.
 *
 * Maintains a [Settlement] per ticket id with its own ChaChaBox keypair. The YAXI
 * gateway routes tickets to specific service instances via sticky routing, so
 * sharing a settlement across tickets would land subsequent requests at instances
 * that have no record of the session.
 */
@InternalRoutexApi
@OptIn(ExperimentalUuidApi::class)
public class RoutexClientCore(
    /**
     * Factory that produces a fresh [Settlement] (with a new ChaChaBox keypair) for
     * each distinct ticket id observed. Called exactly once per ticket id.
     */
    public val settlementFactory: () -> Settlement,
) {
    private val settlements = mutableMapOf<String, Settlement>()
    private val settlementsMutex = Mutex()

    private var redirectUri: String? = null
    private var lastTraceId: TraceId? = null
    private val clientVersion: String = "$routexClientPlatform/$ROUTEX_CLIENT_VERSION"

    /**
     * Trace identifier echoed by the most recent request, decoded from the
     * `yaxi-trace-id` response header. `null` until at least one request completes.
     */
    public val traceId: TraceId?
        get() = lastTraceId

    public fun setRedirectUri(uri: String?) {
        redirectUri = uri
    }

    /**
     * Look up the cached [SystemVersionEntry] for [ticketId], or `null` when no
     * settlement has happened for that ticket yet. Callers that just want the
     * raw JSON should `RoutexJson.encodeToString` it.
     */
    public fun systemVersionFor(ticketId: String): SystemVersionEntry? = settlements[ticketId]?.systemVersion

    /**
     * Settle (idempotent) the [ticketId]'s session and seal [plaintext] against it.
     * Wraps [Settlement.seal] in a `RoutexClientError.SealingFailed` translator so
     * callers see a typed failure.
     */
    public suspend fun sealPayload(
        ticketId: String,
        plaintext: ByteArray,
    ): ByteArray {
        val settlement = getOrCreateSettlement(ticketId)
        settle(settlement, ticketId)
        return sealWith(settlement, plaintext)
    }

    public suspend fun request(
        ticket: Ticket,
        path: String,
        body: ByteArray?,
    ): ByteArray {
        val ticketId = ticket.id.toString()
        val settlement = getOrCreateSettlement(ticketId)
        val settle = settle(settlement, ticketId)

        val sealedTicket = sealWith(settlement, ticket.raw.encodeToByteArray())
        val sealedBody = body?.let { sealWith(settlement, it) }
        val requestHeaders =
            headers {
                append(HttpHeaders.UserAgent, USER_AGENT)
                append(HEADER_CLIENT_VERSION, clientVersion)
                append(HEADER_TICKET_ID, ticketId)
                append(HEADER_TICKET, Base64.Default.encode(sealedTicket))
                append(HEADER_SESSION_ID, settle.sessionId)
                append(HttpHeaders.Accept, MEDIA_TYPE)
                redirectUri?.let { append(HEADER_REDIRECT_URI, it) }
                if (body != null) append(HttpHeaders.ContentType, "application/json")
            }

        val response =
            settlement.httpTransport.execute(
                HttpRequest(
                    method = if (body != null) HttpMethod.POST else HttpMethod.GET,
                    url = joinUrl(settlement.baseUrl, path),
                    headers = requestHeaders,
                    body = sealedBody,
                ),
            )

        response.headers[HEADER_TRACE_ID]?.let { header ->
            try {
                val sealed = Base64.Default.decode(header)
                lastTraceId = TraceId(unsealWith(settlement, sealed))
            } catch (_: IllegalArgumentException) {
                // Malformed trace id header; ignore silently.
            } catch (_: RoutexClientError.UnsealingFailed) {
                // Unsealing failed; ignore silently.
            }
        }

        val plain =
            unsealBestEffort(settlement, response.body, fallback = response.status >= HTTP_ERROR_BAD_REQUEST)
        if (response.status >= HTTP_ERROR_BAD_REQUEST) {
            throw RoutexErrorDispatcher.dispatch(response.status, plain.decodeToString())
        }
        return plain
    }

    private suspend fun settle(
        settlement: Settlement,
        ticketId: String,
    ): SettleResult =
        settlement.settle(
            extraHeaders =
                mapOf(
                    HttpHeaders.UserAgent to USER_AGENT,
                    HEADER_CLIENT_VERSION to clientVersion,
                    HEADER_TICKET_ID to ticketId,
                ),
        )

    private suspend fun getOrCreateSettlement(ticketId: String): Settlement =
        settlementsMutex.withLock {
            settlements.getOrPut(ticketId) { settlementFactory() }
        }

    private fun sealWith(
        settlement: Settlement,
        plaintext: ByteArray,
    ): ByteArray =
        try {
            settlement.seal(plaintext)
        } catch (e: Exception) {
            throw RoutexClientError.SealingFailed("ChaChaBox.seal failed: ${e.message}", e)
        }

    private fun unsealWith(
        settlement: Settlement,
        ciphertext: ByteArray,
    ): ByteArray =
        try {
            settlement.unseal(ciphertext)
        } catch (e: Exception) {
            throw RoutexClientError.UnsealingFailed("ChaChaBox.unseal failed: ${e.message}", e)
        }

    private fun unsealBestEffort(
        settlement: Settlement,
        body: ByteArray,
        fallback: Boolean,
    ): ByteArray {
        if (body.isEmpty()) return body
        return try {
            settlement.unseal(body)
        } catch (e: Exception) {
            if (fallback) body else throw RoutexClientError.UnsealingFailed("response unseal failed: ${e.message}", e)
        }
    }
}

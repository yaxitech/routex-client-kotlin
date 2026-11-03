package tech.yaxi.routex.settlement

import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headers
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.RoutexErrorDispatcher
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.settlement.internal.joinUrl
import tech.yaxi.routex.transport.HttpMethod
import tech.yaxi.routex.transport.HttpRequest
import tech.yaxi.routex.transport.HttpTransport

private const val MEDIA_TYPE = "application/vnd.yaxi.v5"
private const val HTTP_ERROR_BAD_REQUEST = 400
internal const val DEFAULT_SETTLEMENT_PATH: String = "key-settlement"

/**
 * TEE-attested key-settlement session against the YAXI Routex service.
 *
 * Consumers typically do not construct [Settlement] directly, the `RoutexClient` /
 * `RoutexRefreshClient` facades do that internally.
 *
 * @suppress
 */
public class Settlement
    internal constructor(
        internal val baseUrl: Url,
        internal val httpTransport: HttpTransport,
        private val signingKeys: Map<String, ByteArray>,
        private val requirements: Requirements = Requirements.Default,
        private val path: String = DEFAULT_SETTLEMENT_PATH,
        private val clientKeys: ChaChaBoxKeys = ChaChaBox.generateKeys(),
    ) {
        /** Production constructor binding the YAXI-operated cryptographic defaults. */
        @InternalRoutexApi
        public constructor(
            baseUrl: Url,
            httpTransport: HttpTransport,
        ) : this(
            baseUrl = baseUrl,
            httpTransport = httpTransport,
            signingKeys = YaxiSystemVersionKeys.DEFAULT,
            requirements = Requirements.Default,
        )

        /**
         * Test-only constructor overriding the system-version verifying keys.
         * @suppress
         */
        @InternalRoutexApi
        public constructor(
            baseUrl: Url,
            httpTransport: HttpTransport,
            signingKeys: Map<String, ByteArray>,
        ) : this(
            baseUrl = baseUrl,
            httpTransport = httpTransport,
            signingKeys = signingKeys,
            requirements = Requirements.Default,
        )

        private val vcekRoots: VcekRootStore = VcekRootStore.Amd
        private val mutex = Mutex()

        @Volatile
        private var settled: SettleResult? = null

        /** True once a successful settlement has been cached. */
        internal val isSettled: Boolean
            get() = settled != null

        /** Session id issued by the server, or null if this instance has not yet settled. */
        internal val sessionId: String?
            get() = settled?.sessionId

        /** Authenticated TEE system version, or null if this instance has not yet settled. */
        internal val systemVersion: SystemVersionEntry?
            get() = settled?.systemVersion

        /**
         * Run the TEE-attested key settlement if it has not been completed yet; otherwise
         * return the cached [SettleResult]. Idempotent and thread-safe.
         *
         * @param extraHeaders Additional request headers to forward on the settlement
         *   POST (e.g. a caller-specific `yaxi-client-version` value, or `yaxi-ticket-id`
         *   for the live observability backend). Only consulted on the call that
         *   actually performs the settlement; subsequent calls return the cached result
         *   and ignore [extraHeaders].
         */
        internal suspend fun settle(extraHeaders: Map<String, String> = emptyMap()): SettleResult =
            mutex.withLock {
                val existing = settled
                if (existing != null) return@withLock existing
                val requestHeaders =
                    headers {
                        append(HttpHeaders.Accept, MEDIA_TYPE)
                        append(HttpHeaders.ContentType, "application/json")
                        extraHeaders.forEach { (name, value) -> append(name, value) }
                    }
                val response =
                    httpTransport.execute(
                        HttpRequest(
                            method = HttpMethod.POST,
                            url = joinUrl(baseUrl, path),
                            headers = requestHeaders,
                            body = settlementRequestBody(clientKeys),
                        ),
                    )
                if (response.status >= HTTP_ERROR_BAD_REQUEST) {
                    throw RoutexErrorDispatcher.dispatch(response.status, response.body.decodeToString())
                }
                val result =
                    KeySettlement.verify(
                        responseBytes = response.body,
                        clientKeys = clientKeys,
                        vcekRoots = vcekRoots,
                        requirements = requirements,
                        systemVersionKeys = signingKeys,
                    )
                settled = result
                result
            }

        /**
         * Seal [plaintext] for transmission to the server. Requires a prior successful
         * call to [settle]; otherwise throws [KeySettlementError.NotReady].
         */
        internal fun seal(plaintext: ByteArray): ByteArray {
            val settle = settled ?: throw KeySettlementError.NotReady()
            return ChaChaBox.seal(settle.serverPublicKey, plaintext)
        }

        /**
         * Unseal [ciphertext] previously sealed against this session's client keypair.
         * Available pre-settle because the recipient-side key material is generated at
         * construction time.
         */
        internal fun unseal(ciphertext: ByteArray): ByteArray = ChaChaBox.unseal(clientKeys, ciphertext)

        internal val clientPublicKey: ChaChaBoxPublicKey
            get() = clientKeys.publicKey
    }

internal fun settlementRequestBody(clientKeys: ChaChaBoxKeys): ByteArray {
    val publicKey = clientKeys.publicKey
    val obj =
        buildJsonObject {
            put("publicKey", Base64.Default.encode(publicKey.toBytes()))
            put("version", publicKey.version)
        }
    return RoutexJson.encodeToString(JsonObject.serializer(), obj).encodeToByteArray()
}

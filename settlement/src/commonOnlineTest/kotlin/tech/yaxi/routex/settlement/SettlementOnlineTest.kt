package tech.yaxi.routex.settlement

import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import tech.yaxi.routex.models.Base64UrlNoPad
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.testing.internal.TestCredentials
import tech.yaxi.routex.transport.defaultHttpTransport

/**
 * End-to-end exercise of [Settlement] against the live YAXI staging API. Credentials
 * come from the build-time-generated [TestCredentials]; the `:jvmOnlineTest` Gradle
 * task fails fast when `local.properties` is missing the credentials, so this test
 * can assume they are populated.
 */
@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class SettlementOnlineTest {
    @Test
    @OptIn(InternalRoutexApi::class)
    fun settleAgainstLiveApiYieldsSystemVersionAnd32ByteSessionId() =
        runTest {
            val settlement =
                Settlement(
                    baseUrl = apiUrl(),
                    httpTransport = defaultHttpTransport(),
                    signingKeys = systemVersionSigningKeys(),
                )
            val ticket = accountsTicket()
            val ticketId = extractTicketId(ticket)
            val result = settlement.settle(extraHeaders = mapOf("yaxi-ticket-id" to ticketId))
            val sessionBytes = Base64.Default.decode(result.sessionId)
            assertEquals(SESSION_ID_BYTE_LENGTH, sessionBytes.size)
            assertNotNull(settlement.systemVersion)
            assertTrue(settlement.isSettled)
            assertEquals(result.sessionId, settlement.sessionId)
        }

    @Test
    @OptIn(InternalRoutexApi::class)
    fun settleFailsWhenSystemVersionSignatureDoesNotVerify() =
        runTest {
            // Tamper the verifying-key bytes while keeping the wire keyIds intact, so the
            // SystemVersion key lookup hits but Ed25519 verification rejects the signature.
            val tamperedKeys =
                if (TestCredentials.TEST_SIGNING_KEYS) {
                    mapOf("dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg=" to ByteArray(32))
                } else {
                    YaxiSystemVersionKeys.DEFAULT.mapValues { ByteArray(32) }
                }
            val settlement =
                Settlement(
                    baseUrl = apiUrl(),
                    httpTransport = defaultHttpTransport(),
                    signingKeys = tamperedKeys,
                )
            val ticket = accountsTicket()
            val ticketId = extractTicketId(ticket)
            assertFailsWith<KeySettlementError.SystemVersionInvalid> {
                settlement.settle(extraHeaders = mapOf("yaxi-ticket-id" to ticketId))
            }
            assertEquals(false, settlement.isSettled)
        }

    private fun apiUrl(): Url = Url(TestCredentials.URL)

    private fun apiKeyId(): String = TestCredentials.API_KEY_ID

    private fun apiKeySecret(): ByteArray = Base64.Default.decode(TestCredentials.API_KEY)

    /**
     * Pick the verifying-key map matching the live environment: in CI the test signs
     * with a single staging key id (mirrors the CI key in
     * `OnlineTestSupport.systemVersionSigningKeys`); production envs use the built-in
     * [YaxiSystemVersionKeys.DEFAULT].
     */
    private fun systemVersionSigningKeys(): Map<String, ByteArray> =
        if (TestCredentials.TEST_SIGNING_KEYS) {
            mapOf(CI_TEST_SIGNING_KEY_ID to Base64.Default.decode(CI_TEST_SIGNING_KEY_BASE64))
        } else {
            YaxiSystemVersionKeys.DEFAULT
        }

    private fun accountsTicket(): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val claims =
            buildJsonObject {
                put(
                    "data",
                    buildJsonObject {
                        put("service", "Accounts")
                        put("id", Uuid.random().toString())
                        put("data", JsonNull)
                    },
                )
                put("exp", (now / MILLIS_PER_SECOND) + TICKET_TTL_SECONDS)
            }
        val claimsJson = RoutexJson.encodeToString(JsonObject.serializer(), claims)
        val header =
            Base64UrlNoPad.encode(
                """{"alg":"HS256","typ":"JWT","kid":"${apiKeyId()}"}""".encodeToByteArray(),
            )
        val payload = Base64UrlNoPad.encode(claimsJson.encodeToByteArray())
        val signingInput = "$header.$payload"
        val mac = HmacSHA256(apiKeySecret())
        mac.update(signingInput.encodeToByteArray())
        val signature = Base64UrlNoPad.encode(mac.doFinal())
        return "$signingInput.$signature"
    }

    private fun extractTicketId(jwt: String): String {
        val parts = jwt.split(".")
        val payload = Base64UrlNoPad.decode(parts[1]).decodeToString()
        val match = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(payload) ?: error("id not found in ticket payload")
        return match.groupValues[1]
    }

    private companion object {
        const val SESSION_ID_BYTE_LENGTH: Int = OnlineTestConstants.SESSION_ID_BYTE_LENGTH
        const val TICKET_TTL_SECONDS: Long = 60
        const val MILLIS_PER_SECOND: Long = 1_000

        const val CI_TEST_SIGNING_KEY_ID: String = "dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg="
        const val CI_TEST_SIGNING_KEY_BASE64: String = "qzLgDnRegbiQzY416i9/MClrmMp24jcHzaWCWSWSutA="
    }
}

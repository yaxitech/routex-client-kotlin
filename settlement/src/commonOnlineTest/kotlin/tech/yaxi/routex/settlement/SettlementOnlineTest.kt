package tech.yaxi.routex.settlement

import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.testing.internal.TestCredentials
import tech.yaxi.routex.tickets.RoutexTicketIssuer
import tech.yaxi.routex.transport.defaultHttpTransport

/**
 * End-to-end exercise of [Settlement] against the live YAXI staging API. Credentials
 * come from the build-time-generated [TestCredentials]; the `:jvmOnlineTest` Gradle
 * task fails fast when `local.properties` is missing the credentials, so this test
 * can assume they are populated.
 */
@OnlineTest
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
            val ticket = ticketIssuer.accounts()
            val result = settlement.settle(extraHeaders = mapOf("yaxi-ticket-id" to ticket.id.toString()))
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
            val ticket = ticketIssuer.accounts()
            assertFailsWith<KeySettlementError.SystemVersionInvalid> {
                settlement.settle(extraHeaders = mapOf("yaxi-ticket-id" to ticket.id.toString()))
            }
            assertEquals(false, settlement.isSettled)
        }

    private fun apiUrl(): Url = Url(TestCredentials.URL)

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

    private val ticketIssuer: RoutexTicketIssuer by lazy {
        RoutexTicketIssuer(apiKeyId = TestCredentials.API_KEY_ID, base64Secret = TestCredentials.API_KEY)
    }

    private companion object {
        const val SESSION_ID_BYTE_LENGTH: Int = OnlineTestConstants.SESSION_ID_BYTE_LENGTH

        const val CI_TEST_SIGNING_KEY_ID: String = "dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg="
        const val CI_TEST_SIGNING_KEY_BASE64: String = "qzLgDnRegbiQzY416i9/MClrmMp24jcHzaWCWSWSutA="
    }
}

package tech.yaxi.routex.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * End-to-end smoke for the typed `Authenticated<T>` + `Response<T>` round-trip:
 * synth a wire-shape `Result` payload carrying a JWT whose `data` claim is an
 * [AccountsResult], decode via [decodeResponse], and assert both the raw JWT and
 * the lazily-parsed typed payload survive intact.
 */
@OptIn(ExperimentalUuidApi::class)
class AuthenticatedTest {
    @Test
    fun decodeResponseExtractsTypedAccountsResultFromJwt() {
        val account =
            Account(
                iban = "DE02120300000000202051",
                bic = "BYLADEM1001",
                currency = "EUR",
                ownerName = "Dr. Peter Steiger",
            )
        val accountsResult =
            AccountsResult(
                data = listOf(account),
                ticketId = Uuid.parse("11111111-2222-3333-4444-555555555555"),
                timestamp = Instant.parse("2026-04-26T12:34:56Z"),
            )
        val jwt = synthesiseJwt(AccountsResult.serializer(), accountsResult)
        val wire =
            buildJsonObject {
                put(
                    "Result",
                    buildJsonArray {
                        add(JsonPrimitive(jwt))
                        add(JsonNull)
                        add(JsonNull)
                    },
                )
            }

        val response =
            decodeResponse(
                RoutexJson.encodeToString(JsonObject.serializer(), wire),
                AccountsResult.serializer(),
            )
        val result = response as Response.Result<AccountsResult>

        assertEquals(jwt, result.authenticated.jwt, "raw JWT should round-trip verbatim")
        assertEquals(
            accountsResult,
            result.authenticated.decodeUnverified(),
            "lazy decodeUnverified() should reproduce the embedded AccountsResult",
        )
        assertNull(result.session)
        assertNull(result.connectionData)
    }

    @Test
    fun decodeUnverifiedIsLazyAndIdempotent() {
        val jwt = synthesiseJwt(String.serializer(), "hello")
        val authenticated =
            Authenticated(jwt) { decodeJwtDataClaim(it, String.serializer()) }

        // Two calls must succeed independently, decodeUnverified re-parses each time, by design.
        assertEquals("hello", authenticated.decodeUnverified())
        assertEquals("hello", authenticated.decodeUnverified())
        assertEquals(jwt, authenticated.jwt)
    }

    @Test
    fun decodeJwtDataClaimRejectsMalformedJwts() {
        assertFails { decodeJwtDataClaim("only-one-segment", String.serializer()) }
        // Valid base64url, but body is not JSON.
        val notJson =
            "${Base64UrlNoPad.encode(JWT_HEADER.encodeToByteArray())}." +
                "${Base64UrlNoPad.encode("not-json".encodeToByteArray())}.x"
        assertFails { decodeJwtDataClaim(notJson, String.serializer()) }
        // Valid JSON, missing `data` claim.
        val noData =
            "${Base64UrlNoPad.encode(JWT_HEADER.encodeToByteArray())}." +
                "${Base64UrlNoPad.encode("""{"exp":$SOMEDAY_2050}""".encodeToByteArray())}.x"
        val ex = assertFails { decodeJwtDataClaim(noData, String.serializer()) }
        assertTrue(ex is AuthenticatedDecodeError, "expected AuthenticatedDecodeError, got $ex")
    }

    /**
     * Produce a `<headerB64Url>.<payloadB64Url>.<fakeSignature>` triple where the JWT body
     * carries [data] as the `data` claim, matching the wire shape that
     * [decodeJwtDataClaim] expects. Signature is a literal, clients never verify on
     * decode (that's a trusted-backend concern).
     */
    private fun <T> synthesiseJwt(
        serializer: kotlinx.serialization.KSerializer<T>,
        data: T,
    ): String {
        val payload =
            buildJsonObject {
                put("data", RoutexJson.encodeToJsonElement(serializer, data))
                put("exp", JsonPrimitive(SOMEDAY_2050))
            }
        val payloadJson = RoutexJson.encodeToString(JsonObject.serializer(), payload)
        val header = Base64UrlNoPad.encode(JWT_HEADER.encodeToByteArray())
        val body = Base64UrlNoPad.encode(payloadJson.encodeToByteArray())
        return "$header.$body.fake-signature"
    }

    private companion object {
        const val SOMEDAY_2050: Long = 2_540_808_000L
        const val JWT_HEADER: String = """{"alg":"HS256","typ":"JWT","kid":"test"}"""
    }
}

package tech.yaxi.routex.client

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.Ticket

/**
 * Offline host-side smoke for the public [RoutexClient] surface. Runs on the Android
 * JVM unit-test variant (`testAndroidHostTest`) without a device, emulator, or live
 * YAXI credentials, so `./gradlew :client:check` always has an Android signal.
 */
@OptIn(ExperimentalUuidApi::class)
class RoutexClientHostTest {
    @Test
    fun freshClientHasNoTraceId() {
        val client = RoutexClient()
        assertNull(client.traceId)
    }

    @Test
    fun ticketParseRecognizesService() {
        val ticket = Ticket.parse(makeJwt(service = "Accounts"))
        assertIs<Ticket.Accounts>(ticket)
    }

    @Test
    fun systemVersionIsNullBeforeFirstRequest() {
        val client = RoutexClient()
        val ticket = Ticket.Accounts(makeJwt(service = "Accounts"))
        assertNull(client.systemVersion(ticket))
    }

    private fun makeJwt(service: String): String {
        val header = base64Url(RoutexJson.encodeToString(JsonObject.serializer(), HEADER_CLAIM))
        val payload =
            base64Url(
                RoutexJson.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonObject {
                                put("service", service)
                                put("id", Uuid.random().toString())
                            },
                        )
                    },
                ),
            )
        // Signature is opaque: parsing only validates the payload's structure.
        val signature = base64Url("offline-host-test")
        return "$header.$payload.$signature"
    }

    private fun base64Url(input: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(input.encodeToByteArray())

    private companion object {
        val HEADER_CLAIM: JsonObject =
            buildJsonObject {
                put("alg", "HS256")
                put("typ", "JWT")
            }
    }
}

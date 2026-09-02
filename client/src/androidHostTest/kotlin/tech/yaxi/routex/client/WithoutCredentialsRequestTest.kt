package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.yaxi.routex.client.internal.prepareAccountsWithoutCredentials
import tech.yaxi.routex.client.internal.prepareBalancesWithoutCredentials
import tech.yaxi.routex.client.internal.prepareTransactionsWithoutCredentials
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.NonInteractiveAccountsRequest
import tech.yaxi.routex.models.NonInteractiveBalancesRequest
import tech.yaxi.routex.models.NonInteractiveTransactionsRequest
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.Session

/**
 * The bodies of the requests without credentials, which routex tells apart from the
 * interactive ones by the absence of `credentials`. Getting this wrong makes every such
 * call fail, so pin the shape offline rather than only in the online suite.
 */
class WithoutCredentialsRequestTest {
    // The constructors are internal to :models, so go through the serializers.
    private val connectionData: ConnectionData =
        RoutexJson.decodeFromString(ConnectionData.serializer(), "\"AQID\"")
    private val session: Session = RoutexJson.decodeFromString(Session.serializer(), "\"BAUG\"")

    private fun <T> encode(
        serializer: KSerializer<T>,
        body: T,
    ): JsonObject = RoutexJson.encodeToJsonElement(serializer, body) as JsonObject

    @Test
    fun accountsBodyCarriesConnectionDataAndNoCredentials() {
        val json =
            encode(
                NonInteractiveAccountsRequest.serializer(),
                prepareAccountsWithoutCredentials(
                    connectionData = connectionData,
                    session = null,
                    fields = listOf(AccountField.Iban),
                    filter = null,
                ),
            )

        assertFalse("credentials" in json, "the body must not carry credentials")
        assertEquals("AQID", json["connectionData"]?.jsonPrimitive?.content)
        assertFalse("recurringConsents" in json, "no consent gets established")
    }

    @Test
    fun balancesBodyCarriesConnectionDataAndSession() {
        val json =
            encode(
                NonInteractiveBalancesRequest.serializer(),
                prepareBalancesWithoutCredentials(
                    connectionData = connectionData,
                    session = session,
                    accounts = listOf(AccountReference.iban("DE02120300000000202051", "EUR")),
                ),
            )

        assertFalse("credentials" in json, "the body must not carry credentials")
        assertEquals("AQID", json["connectionData"]?.jsonPrimitive?.content)
        assertEquals("BAUG", json["session"]?.jsonPrimitive?.content)
    }

    @Test
    fun transactionsBodyCarriesConnectionDataAndNoCredentials() {
        val json =
            encode(
                NonInteractiveTransactionsRequest.serializer(),
                prepareTransactionsWithoutCredentials(
                    connectionData = connectionData,
                    session = null,
                ),
            )

        assertFalse("credentials" in json, "the body must not carry credentials")
        assertEquals("AQID", json["connectionData"]?.jsonPrimitive?.content)
    }
}

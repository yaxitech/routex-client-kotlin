package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.SearchFilter

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class SearchEncryptedIbanOnlineTest {
    @Test
    fun encryptedIbanSearchResolvesToSingleConnection() =
        runTest {
            val client =
                OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.ticketIssuer.accounts()

            val encrypted =
                encryptOnFileData(
                    plaintext = "DE02120300000000202051".encodeToByteArray(),
                    secret = OnlineTestSupport.apiKeySecret(),
                )

            val results =
                client.search(
                    ticket = ticket,
                    filters = listOf(SearchFilter.EncryptedIban(encrypted)),
                    ibanDetection = true,
                )
            assertEquals(1, results.size)
            val hit = results[0]
            assertEquals(ConnectionId("connection-c5d149fe-02fe-4fc6-b83c-21572d389967"), hit.id)
            assertEquals("Deutsche Kreditbank Berlin", hit.displayName)
            assertEquals("dkb", hit.logoId)
            assertEquals(listOf("DE"), hit.countries)
        }
}

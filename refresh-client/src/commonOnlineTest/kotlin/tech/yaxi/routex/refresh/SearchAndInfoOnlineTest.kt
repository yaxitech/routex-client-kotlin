package tech.yaxi.routex.refresh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.ConnectionInfo
import tech.yaxi.routex.models.SearchFilter

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class SearchAndInfoOnlineTest {
    private val routexRefreshClient = OnlineTestSupport.refreshClient()
    private val accountsTicket = OnlineTestSupport.ticketIssuer.accounts()

    private fun assertDemoConnectionInfo(
        info: ConnectionInfo,
        expectedBics: List<String>?,
        expectedBankCodes: List<String>?,
    ) {
        assertEquals(OnlineTestSupport.DEMO_CONNECTION_ID, info.id)
        assertEquals(emptyList(), info.countries)
        assertEquals("YAXI Demo (user)", info.displayName)
        assertEquals(false, info.credentials.full)
        assertEquals(true, info.credentials.userId)
        assertEquals(false, info.credentials.none)
        assertEquals("User ID", info.userId)
        assertEquals("Password", info.password)
        assertEquals("yaxi", info.logoId)
        assertEquals(expectedBics, info.bics)
        assertEquals(expectedBankCodes, info.bankCodes)
    }

    @Test
    fun search() =
        runTest {
            val infos =
                routexRefreshClient.search(
                    ticket = accountsTicket,
                    filters = listOf(SearchFilter.Term("YAXI Demo (user)")),
                )
            assertEquals(1, infos.size)
            assertDemoConnectionInfo(infos.first(), expectedBics = null, expectedBankCodes = null)
        }

    @Test
    fun info() =
        runTest {
            val demoInfo =
                routexRefreshClient.info(
                    ticket = accountsTicket,
                    connectionId = OnlineTestSupport.DEMO_CONNECTION_ID,
                )
            assertDemoConnectionInfo(demoInfo, expectedBics = emptyList(), expectedBankCodes = emptyList())
        }

    @Test
    fun systemVersion() =
        runTest {
            // Make sure a settlement has happened
            routexRefreshClient.info(
                ticket = accountsTicket,
                connectionId = OnlineTestSupport.DEMO_CONNECTION_ID,
            )

            val systemVersion = routexRefreshClient.systemVersion(accountsTicket)
            assertNotNull(systemVersion, "Expected a system version for ticket ${accountsTicket.id}")
        }
}

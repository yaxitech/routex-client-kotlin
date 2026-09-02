package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.SearchFilter

@OnlineTest
@OptIn(ExperimentalUuidApi::class)
class SearchOnlineTest {
    @Test
    fun sparkasseSearchReturnsTwentyMatches() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.ticketIssuer.accounts()

            val filters =
                "sparkasse stadt".split("\\s+".toRegex()).map { SearchFilter.Term(it) }

            val results =
                client.search(
                    ticket = ticket,
                    filters = filters,
                    ibanDetection = true,
                    limit = 20u,
                )

            assertEquals(20, results.size)
            assertTrue(
                results.any { it.id == ConnectionId("connection-3c0ec6db-f1e0-4bfb-be0b-4e5af8a7ad4e") },
                "expected Sparkasse Duderstadt in results",
            )
            assertTrue(
                results.all { it.logoId == "sparkasse" },
                "all results should share the sparkasse logo id: $results",
            )
            results.forEach {
                assertEquals(listOf("DE"), it.countries)
            }
        }
}

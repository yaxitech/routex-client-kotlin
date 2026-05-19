package tech.yaxi.routex.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.Response

/**
 * Android instrumentation smoke. Exercises the public [RoutexClient] surface from
 * Kotlin against the live YAXI demo connection so a CI run on an emulator (or a
 * developer's connected device) catches Android-specific runtime regressions on top
 * of the shared online tests.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalUuidApi::class)
class AndroidAccountsSmokeTest {
    @Test
    fun accountsAgainstDemoConnectionReturnsResultWithExpectedShape() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.ticketIssuer.accounts()

            val response =
                client.accounts(
                    ticket = ticket,
                    credentials =
                        Credentials(
                            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                            userId = "result",
                        ),
                    fields = listOf(AccountField.Iban, AccountField.OwnerName, AccountField.Currency),
                )

            val payload = assertIs<Response.Result<AccountsResult>>(response).authenticated.decodeUnverified()
            assertTrue(payload.data.isNotEmpty(), "expected at least one account, got ${payload.data}")
            val first = payload.data[0]
            assertEquals("DE02120300000000202051", first.iban)
            assertEquals("EUR", first.currency)
            assertEquals("Dr. Peter Steiger", first.ownerName)
        }
}

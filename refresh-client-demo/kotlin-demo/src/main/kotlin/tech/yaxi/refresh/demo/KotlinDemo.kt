@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package tech.yaxi.refresh.demo

import kotlinx.coroutines.runBlocking
import tech.yaxi.routex.client.RoutexClient
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountIdentifier
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.Balances
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.refresh.RoutexRefreshClient
import tech.yaxi.routex.tickets.RoutexTicketIssuer

/** YAXI Demo Bank, pre-wired to return canned data without contacting a real bank. */
private const val DEMO_CONNECTION_ID = "connection-96386142-60e5-4ca9-abcf-944efce5bc1e"

/**
 * Kotlin demo for `routex-refresh-client`: opens a one-shot consent against YAXI's Demo
 * Bank to obtain a [ConnectionData], then uses it to refresh balances non-interactively.
 * In production, the consent half runs on the user's device and the refresh half runs on
 * your backend.
 */
fun main(): Unit =
    runBlocking {
        val creds = EnvCredentials.fromEnv()
        val issuer = RoutexTicketIssuer(apiKeyId = creds.apiKeyId, apiKeySecret = creds.apiKeySecret)

        val client = RoutexClient(creds.apiUrl)
        val refreshClient = RoutexRefreshClient(creds.apiUrl)

        val refreshContext = obtainConnectionData(client, issuer)
        val balances = refreshBalances(refreshClient, issuer, refreshContext)
        printBalances(balances)
    }

/** Step 1: interactive accounts call with `recurringConsents=true` yields a reusable [ConnectionData]. */
private suspend fun obtainConnectionData(
    client: RoutexClient,
    issuer: RoutexTicketIssuer,
): RefreshContext {
    val ticket = issuer.accounts()
    val credentials = Credentials(connectionId = ConnectionId.parse(DEMO_CONNECTION_ID), userId = "result")
    val fields = listOf(AccountField.Iban, AccountField.Currency, AccountField.Name)

    return when (val response = client.accounts(ticket, credentials, fields, recurringConsents = true)) {
        is Response.Result<AccountsResult> -> {
            val connectionData =
                response.connectionData
                    ?: error("server did not return connectionData; recurringConsents not granted")
            val refs =
                response.authenticated
                    .decodeUnverified()
                    .data
                    .filter { it.iban != null }
                    .map { AccountReference.iban(it.iban!!, it.currency) }
            RefreshContext(connectionData, refs)
        }
        is Response.Dialog, is Response.Redirect, is Response.RedirectHandle ->
            TODO("expected a Result, got ${response::class.simpleName}")
    }
}

/** Step 2: non-interactive balances call using the [ConnectionData] obtained above. */
private suspend fun refreshBalances(
    client: RoutexRefreshClient,
    issuer: RoutexTicketIssuer,
    refreshContext: RefreshContext,
): Balances {
    val ticket = issuer.balances()
    return client.balances(ticket, refreshContext.connectionData, refreshContext.accountRefs).result
}

private fun printBalances(balances: Balances) {
    for (perAccount in balances.balances) {
        val identifier =
            when (val id = perAccount.account.id) {
                is AccountIdentifier.Iban -> id.value
                is AccountIdentifier.Number -> id.value
            }
        for (balance in perAccount.balances) {
            println(
                "%-9s %s %s  (%s)".format(
                    balance.balanceType,
                    balance.amount.toPlainString(),
                    balance.currency,
                    identifier,
                ),
            )
        }
    }
}

/** Pair of [ConnectionData] and the account references derived from the interactive accounts response. */
private data class RefreshContext(val connectionData: ConnectionData, val accountRefs: List<AccountReference>)

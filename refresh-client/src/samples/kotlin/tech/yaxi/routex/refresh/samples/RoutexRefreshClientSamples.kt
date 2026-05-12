@file:Suppress("UnusedVariable", "UNUSED_VARIABLE")

package tech.yaxi.routex.refresh.samples

import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.ConnectionDetails
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.SearchFilter
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.refresh.RoutexRefreshClient

internal object RoutexRefreshClientSamples {
    suspend fun refreshAccounts(
        client: RoutexRefreshClient,
        ticket: Ticket.Accounts,
        connectionData: ConnectionData,
    ) {
        val response =
            client.accounts(
                ticket = ticket,
                connectionData = connectionData,
                fields = listOf(AccountField.Iban, AccountField.OwnerName, AccountField.Currency),
            )
    }

    suspend fun refreshBalances(
        client: RoutexRefreshClient,
        ticket: Ticket.Balances,
        connectionData: ConnectionData,
        accounts: List<AccountReference>,
    ) {
        val response =
            client.balances(
                ticket = ticket,
                connectionData = connectionData,
                accounts = accounts,
            )
    }

    suspend fun refreshTransactions(
        client: RoutexRefreshClient,
        ticket: Ticket.Transactions,
        connectionData: ConnectionData,
    ) {
        val response =
            client.transactions(
                ticket = ticket,
                connectionData = connectionData,
            )
    }

    suspend fun searchConnections(
        client: RoutexRefreshClient,
        ticket: Ticket,
    ) {
        val results =
            client.search(
                ticket = ticket,
                filters = listOf(SearchFilter.Term("sparkasse")),
                limit = 10u,
                details = listOf(ConnectionDetails.BICS),
            )
    }

    suspend fun connectionInfo(
        client: RoutexRefreshClient,
        ticket: Ticket,
        connectionId: ConnectionId,
    ) {
        val info = client.info(ticket = ticket, connectionId = connectionId)
    }
}

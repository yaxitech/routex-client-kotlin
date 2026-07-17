package tech.yaxi.routex.refresh.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountFilter
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.NonInteractiveAccountsRequest
import tech.yaxi.routex.models.NonInteractiveBalancesRequest
import tech.yaxi.routex.models.NonInteractiveResponse
import tech.yaxi.routex.models.NonInteractiveTransactionsRequest
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.Session
import tech.yaxi.routex.models.UserInSession
import tech.yaxi.routex.settlement.RoutexClientError

internal fun prepareNonInteractiveAccounts(
    connectionData: ConnectionData,
    session: Session?,
    userInSession: UserInSession?,
    fields: List<AccountField<*>>,
    filter: AccountFilter?,
): NonInteractiveAccountsRequest =
    NonInteractiveAccountsRequest(
        connectionData = connectionData,
        session = session,
        userInSession = userInSession,
        fields = fields,
        filter = filter,
    )

internal fun prepareNonInteractiveBalances(
    connectionData: ConnectionData,
    session: Session?,
    userInSession: UserInSession?,
    accounts: List<AccountReference>,
): NonInteractiveBalancesRequest =
    NonInteractiveBalancesRequest(
        connectionData = connectionData,
        session = session,
        userInSession = userInSession,
        accounts = accounts,
    )

internal fun prepareNonInteractiveTransactions(
    connectionData: ConnectionData,
    session: Session?,
    userInSession: UserInSession?,
): NonInteractiveTransactionsRequest =
    NonInteractiveTransactionsRequest(
        connectionData = connectionData,
        session = session,
        userInSession = userInSession,
    )

internal fun <T> readNonInteractiveResponse(
    bodyBytes: ByteArray,
    resultSerializer: KSerializer<T>,
): NonInteractiveResponse<T> {
    val text = bodyBytes.decodeToString()
    val serializer = NonInteractiveResponse.serializer(resultSerializer)
    return try {
        RoutexJson.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        throw RoutexClientError.MalformedResponse("Failed to decode NonInteractiveResponse: ${e.message}", e)
    }
}

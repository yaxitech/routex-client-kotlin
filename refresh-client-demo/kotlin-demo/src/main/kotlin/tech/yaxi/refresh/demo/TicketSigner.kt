package tech.yaxi.refresh.demo

import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import tech.yaxi.routex.models.Ticket

/**
 * Mints HS256-signed YAXI tickets without external dependencies.
 *
 * Equivalent to the `io.jsonwebtoken`-based `TicketGenerator` in the YAXI documentation;
 * uses the JDK's stdlib so the demo has no extra dependencies.
 *
 * Typed convenience methods cover the two services this demo exercises. To support
 * other services (CollectPayment / Transactions / Transfer), wrap [issueTicket]
 * directly:
 *
 * ```
 * fun issueTransactionsTicket(dataJson: String): Ticket.Transactions =
 *     Ticket.Transactions(issueTicket(UUID.randomUUID().toString(), "Transactions", dataJson))
 * ```
 */
class TicketSigner(private val keyId: String, private val secret: ByteArray) {
    /** Mint a fresh accounts ticket. */
    fun issueAccountsTicket(): Ticket.Accounts =
        Ticket.Accounts(issueTicket(UUID.randomUUID().toString(), "Accounts"))

    /** Mint a fresh balances ticket. */
    fun issueBalancesTicket(): Ticket.Balances =
        Ticket.Balances(issueTicket(UUID.randomUUID().toString(), "Balances"))

    /**
     * Generic ticket minter. Wrap with a typed factory like [issueAccountsTicket] for
     * each service you call. [dataJson] is a pre-formatted JSON literal; the default
     * `"null"` covers services that take no per-ticket data.
     */
    fun issueTicket(ticketId: String, service: String, dataJson: String = "null"): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString(
            """{"alg":"HS256","typ":"JWT","kid":"$keyId"}""".toByteArray(),
        )
        val exp = System.currentTimeMillis() / 1000L + TICKET_TTL_SECONDS
        val payload = encoder.encodeToString(
            """{"data":{"service":"$service","id":"$ticketId","data":$dataJson},"exp":$exp}""".toByteArray(),
        )
        val signingInput = "$header.$payload"
        val signature = encoder.encodeToString(hmacSha256(signingInput))
        return "$signingInput.$signature"
    }

    private fun hmacSha256(signingInput: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(signingInput.toByteArray())
    }

    private companion object {
        const val TICKET_TTL_SECONDS = 10L * 60L
    }
}

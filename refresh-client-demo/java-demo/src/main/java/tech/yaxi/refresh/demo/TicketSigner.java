package tech.yaxi.refresh.demo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tech.yaxi.routex.models.Ticket;

/**
 * Mints HS256-signed YAXI tickets without external dependencies.
 *
 * <p>Equivalent to the {@code io.jsonwebtoken}-based {@code TicketGenerator} in the
 * YAXI documentation; uses the JDK's stdlib so the demo has no extra dependencies.
 *
 * <p>Typed convenience methods cover the two services this demo exercises. To support
 * other services (CollectPayment / Transactions / Transfer), wrap {@link #issueTicket}
 * directly:
 *
 * <pre>{@code
 * public Ticket.Transactions issueTransactionsTicket(String dataJson) {
 *     return new Ticket.Transactions(issueTicket(UUID.randomUUID().toString(), "Transactions", dataJson));
 * }
 * }</pre>
 */
public final class TicketSigner {
    private static final long TICKET_TTL_SECONDS = 10 * 60L;

    private final String keyId;
    private final byte[] secret;

    public TicketSigner(String keyId, byte[] secret) {
        this.keyId = keyId;
        this.secret = secret.clone();
    }

    /** Mint a fresh accounts ticket. */
    public Ticket.Accounts issueAccountsTicket() {
        return new Ticket.Accounts(issueTicket(UUID.randomUUID().toString(), "Accounts", "null"));
    }

    /** Mint a fresh balances ticket. */
    public Ticket.Balances issueBalancesTicket() {
        return new Ticket.Balances(issueTicket(UUID.randomUUID().toString(), "Balances", "null"));
    }

    /**
     * Generic ticket minter. Wrap with a typed factory like {@link #issueAccountsTicket}
     * for each service you call. {@code dataJson} is a pre-formatted JSON literal; pass
     * {@code "null"} when the service takes no per-ticket data.
     */
    public String issueTicket(String ticketId, String service, String dataJson) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString(
            ("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"" + keyId + "\"}").getBytes(StandardCharsets.UTF_8)
        );
        var exp = System.currentTimeMillis() / 1000L + TICKET_TTL_SECONDS;
        var payload = encoder.encodeToString(
            ("{\"data\":{\"service\":\"" + service + "\",\"id\":\"" + ticketId + "\",\"data\":" + dataJson + "},\"exp\":" + exp + "}")
                .getBytes(StandardCharsets.UTF_8)
        );
        var signingInput = header + "." + payload;
        var signature = encoder.encodeToString(hmacSha256(signingInput));
        return signingInput + "." + signature;
    }

    private byte[] hmacSha256(String signingInput) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}

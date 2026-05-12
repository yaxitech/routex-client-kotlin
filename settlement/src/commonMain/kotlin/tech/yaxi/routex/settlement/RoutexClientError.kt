package tech.yaxi.routex.settlement

/**
 * Client-side cryptographic or decoding failures that are independent of the YAXI
 * service's typed error responses (which surface as
 * [RoutexError][tech.yaxi.routex.models.RoutexError]). These are incident-grade;
 * report them with the most recent
 * [traceId][tech.yaxi.routex.client.RoutexClient.traceId].
 */
public sealed class RoutexClientError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** Sealing a request payload with the established session key failed. */
    public class SealingFailed(
        message: String,
        cause: Throwable? = null,
    ) : RoutexClientError(message, cause)

    /** Unsealing a server response with the established session key failed. */
    public class UnsealingFailed(
        message: String,
        cause: Throwable? = null,
    ) : RoutexClientError(message, cause)

    /** Response decoded but did not match the expected shape; treat as a bug report. */
    public class MalformedResponse(
        message: String,
        cause: Throwable? = null,
    ) : RoutexClientError(message, cause)
}

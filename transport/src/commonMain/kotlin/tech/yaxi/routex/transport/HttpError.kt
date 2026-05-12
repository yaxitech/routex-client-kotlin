package tech.yaxi.routex.transport

/**
 * Failure raised by an [HttpTransport] before the YAXI service has had a chance
 * to answer (DNS, TLS, connect, read, write, decode). Non-2xx HTTP statuses do
 * *not* use this hierarchy; [HttpResponse.status] carries them and the client
 * maps them to the appropriate
 * [RoutexError][tech.yaxi.routex.models.RoutexError] subclass.
 */
public sealed class HttpError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** Network or protocol failure raised by the underlying transport engine. */
    public class TransportFailure(
        message: String,
        cause: Throwable? = null,
    ) : HttpError(message, cause)
}

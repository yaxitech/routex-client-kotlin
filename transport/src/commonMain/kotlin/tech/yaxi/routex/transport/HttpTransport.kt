package tech.yaxi.routex.transport

import io.ktor.http.Headers
import io.ktor.http.Url

/** HTTP method used by the YAXI Routex API. The client only issues `GET` and `POST`. */
public enum class HttpMethod {
    GET,
    POST,
}

/** Single HTTP request issued by the [HttpTransport] layer. */
public data class HttpRequest(
    public val method: HttpMethod,
    public val url: Url,
    public val headers: Headers = Headers.Empty,
    public val body: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpRequest) return false
        return method == other.method &&
            url == other.url &&
            headers == other.headers &&
            bodyContentEquals(body, other.body)
    }

    override fun hashCode(): Int {
        var r = method.hashCode()
        r = 31 * r + url.hashCode()
        r = 31 * r + headers.hashCode()
        r = 31 * r + (body?.contentHashCode() ?: 0)
        return r
    }
}

/** Single HTTP response surfaced by the [HttpTransport] layer. */
public data class HttpResponse(
    public val status: Int,
    public val headers: Headers,
    public val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpResponse) return false
        return status == other.status &&
            headers == other.headers &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var r = status
        r = 31 * r + headers.hashCode()
        r = 31 * r + body.contentHashCode()
        return r
    }
}

/**
 * Transport contract for the YAXI Routex client: a single suspend function that
 * issues an [HttpRequest] and returns the corresponding [HttpResponse].
 *
 * The default transport ([defaultHttpTransport]) is a Ktor engine selected per
 * platform (CIO on JVM/Android, Js on JS/browser). Provide a custom implementation when you need a
 * proxy, mTLS, custom timeouts, or a deterministic fake for tests.
 *
 * Implementations must be safe to invoke concurrently from multiple coroutines:
 * the client serializes nothing on its side. Network and protocol failures should
 * surface as [HttpError.TransportFailure]; non-2xx responses are reported via the
 * status code on [HttpResponse] and interpreted by the client.
 */
public interface HttpTransport {
    public suspend fun execute(request: HttpRequest): HttpResponse
}

private fun bodyContentEquals(
    a: ByteArray?,
    b: ByteArray?,
): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    return a.contentEquals(b)
}

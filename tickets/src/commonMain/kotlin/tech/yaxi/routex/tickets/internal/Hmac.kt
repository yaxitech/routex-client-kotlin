package tech.yaxi.routex.tickets.internal

/**
 * Compute HMAC-SHA256 ([RFC 2104](https://datatracker.ietf.org/doc/html/rfc2104))
 * over [message] keyed by [key]. The result is 32 bytes.
 */
internal expect fun hmacSha256(
    key: ByteArray,
    message: ByteArray,
): ByteArray

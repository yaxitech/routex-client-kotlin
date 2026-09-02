package tech.yaxi.routex.transport

/**
 * Returns the built-in HTTP transport for the current platform: Ktor CIO on JVM/Android,
 * Ktor Js (fetch-based) on JS/browser, Ktor Darwin (NSURLSession-based) on Apple.
 * Consumers who need to control engine settings (proxy, TLS pinning, retries, etc.)
 * can implement [HttpTransport] directly.
 */
public expect fun defaultHttpTransport(): HttpTransport

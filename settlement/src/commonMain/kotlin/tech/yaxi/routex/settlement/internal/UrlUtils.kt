package tech.yaxi.routex.settlement.internal

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.takeFrom

/**
 * Resolve a per-request [path] against the session's [base] URL. [path] segments are
 * treated as **already URL-encoded** so callers (e.g. the trace endpoint) can splice
 * percent-encoded values straight into the path without round-tripping through
 * URLBuilder's segment encoder. Absolute URLs in [path] take over wholesale.
 */
internal fun joinUrl(
    base: Url,
    path: String,
): Url {
    if (path.startsWith("http://") || path.startsWith("https://")) return Url(path)
    return URLBuilder().takeFrom(base).appendEncodedPathSegments(path.trimStart('/').split('/')).build()
}

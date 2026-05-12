package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/**
 * User redirect.
 *
 * The user is meant to be sent to [url][tech.yaxi.routex.models.Redirect.url];
 * [context][tech.yaxi.routex.models.Redirect.context] is passed back to the
 * issuing service afterwards to continue the process. A web application directs
 * the user agent to [url][tech.yaxi.routex.models.Redirect.url]; a desktop or
 * mobile application can open it in a browser or inside an element like a
 * WebView.
 */
@ConsistentCopyVisibility
@Serializable
public data class Redirect internal constructor(
    public val url: String,
    public val context: ConfirmationContext,
)

/**
 * Incomplete user redirect.
 *
 * A final redirect URI needs to get registered using
 * [handle][tech.yaxi.routex.models.RedirectHandle.handle] to obtain the URL to
 * send the user to.
 */
@ConsistentCopyVisibility
@Serializable
public data class RedirectHandle internal constructor(
    public val handle: String,
    public val context: ConfirmationContext,
)

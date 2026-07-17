# Module refresh-client

Kotlin client for the YAXI Open Banking _non-interactive_ (refresh) services.

This module hosts [RoutexRefreshClient][tech.yaxi.routex.refresh.RoutexRefreshClient] (Kotlin) and [RoutexRefreshAsyncClient][tech.yaxi.routex.refresh.RoutexRefreshAsyncClient] (Java façade).
It is JVM-only: the typical caller is a backend that schedules recurring account / balance / transaction refreshes against a consent already obtained through the interactive [client][tech.yaxi.routex.client] module.

The driving difference vs. the interactive client:

- Inputs use a [ConnectionData][tech.yaxi.routex.models.ConnectionData] envelope (obtained from a successful interactive flow's [Response.Result.connectionData][tech.yaxi.routex.models.Response.Result.connectionData] and persisted by the caller) instead of [Credentials][tech.yaxi.routex.models.Credentials].
- Each call returns a [NonInteractiveResponse][tech.yaxi.routex.models.NonInteractiveResponse] carrying the typed payload directly.
  There is no JWT envelope and no interrupt loop: the bank either returns data or the call throws a [RoutexError][tech.yaxi.routex.models.RoutexError] subclass.
  [InterruptError][tech.yaxi.routex.models.InterruptError] is the umbrella signal that the bank wants user input on a non-interactive call (TAN prompt, fresh SCA, anything the interactive client would resolve via `respond*` / `confirm*`).
  Both indicate the caller needs to drive a fresh interactive flow.
- Only the read services and the discovery endpoints are exposed; payment and transfer flows are inherently interactive and remain on [RoutexClient][tech.yaxi.routex.client.RoutexClient].
- By default, YAXI assumes a service call happens outside any user session.
  Banks then typically apply strict limits on how often refreshes may occur; once exceeded, the call typically fails with an [AccessExceeded][tech.yaxi.routex.models.AccessExceeded] error.
  In contrast, when a refresh happens within a user session, e.g., the user is viewing your app or triggering a manual refresh, these limits generally do not apply.
  To indicate a user session, pass a [UserInSession][tech.yaxi.routex.models.UserInSession] so YAXI forwards the user's IP to the bank, which prevents that error under normal usage.

See the [RoutexRefreshClient][tech.yaxi.routex.refresh.RoutexRefreshClient] KDoc for the full call-shape description and the expected exceptions (and runnable Kotlin / Java code samples on each anchor method); the per-service reference lives at [Handling sessions](https://docs.yaxi.tech/sessions.html), [Errors](https://docs.yaxi.tech/errors.html).

# Package tech.yaxi.routex.refresh

Non-interactive YAXI routex client and its Java façade.

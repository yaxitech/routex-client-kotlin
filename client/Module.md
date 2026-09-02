# Module client

Kotlin Multiplatform client for the YAXI Open Banking _interactive_ services.

This module hosts [RoutexClient][tech.yaxi.routex.client.RoutexClient] (Kotlin; JVM, Android, JS, and Apple targets) and [RoutexAsyncClient][tech.yaxi.routex.client.RoutexAsyncClient] (JVM-only Java façade).
Use this module whenever the bank may need to prompt the user for input (credentials, OTPs, redirects, and similar): every service call returns a [Response][tech.yaxi.routex.models.Response] that is either a final [Response.Result][tech.yaxi.routex.models.Response.Result] or an interrupt that the caller resolves via the matching `respond*` / `confirm*` methods.

For unattended refreshes against an already-consented connection, use the companion [refresh-client][tech.yaxi.routex.refresh] module instead.

See the [RoutexClient][tech.yaxi.routex.client.RoutexClient] KDoc for the full call-shape description (and runnable Kotlin / Java code samples on each anchor method); the per-service reference lives at [Getting started](https://docs.yaxi.tech/getting-started.html), [Providing Credentials](https://docs.yaxi.tech/credentials.html), [Handling interrupts](https://docs.yaxi.tech/interrupts.html), [Handling sessions](https://docs.yaxi.tech/sessions.html), [Verify results](https://docs.yaxi.tech/verify.html), [Errors](https://docs.yaxi.tech/errors.html).

# Package tech.yaxi.routex.client

Interactive YAXI routex client and its Java façade.

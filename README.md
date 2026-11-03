# routex-client-kotlin

Kotlin Multiplatform client library for [YAXI's](https://yaxi.tech) Open Banking services.

Ships with JVM, Android, and Apple (macOS and iOS) targets.
The Apple targets are Apple Silicon only (`macosArm64`, `iosArm64`, `iosSimulatorArm64`) and require macOS 12 / iOS 15 or newer.
Service methods are `suspend` functions and integrate with structured concurrency on Kotlin coroutines.
Java consumers should use the `RoutexAsyncClient` facade backed by `CompletableFuture`.

A companion `tech.yaxi:routex-refresh-client` artifact (JVM only) drives non-interactive refreshes from a backend against a connection that already went through an interactive consent.
See [`refresh-client-demo/`](refresh-client-demo/) for a runnable Java and Kotlin example.

See the [documentation](https://docs.yaxi.tech) for the full API reference.

## Installation

Requires JDK 17 or newer and Android `minSdk` 24.

```kotlin
dependencies {
    implementation("tech.yaxi:routex-client:0.9.1")
}
```

## Usage

```kotlin
import tech.yaxi.routex.client.RoutexClient
import tech.yaxi.routex.models.*

// Pass baseUrl = "https://integration.yaxi.tech" for the integration environment
val client = RoutexClient()

// ticket: a YAXI service ticket issued by your backend (see docs)

// Search for a bank
val connections = client.search(
    ticket,
    filters = listOf(SearchFilter.Term("sparkasse")),
    ibanDetection = true,
    limit = 20u,
)

// Fetch accounts
var response = client.accounts(
    accountsTicket,
    Credentials(connectionId, userId = "user"),
    fields = listOf(AccountField.Iban, AccountField.Currency, AccountField.OwnerName),
)

// Handle interrupts (dialogs, redirects)
when (val r = response) {
    is Response.Dialog -> response = when (val input = r.dialog.input) {
        is DialogInput.Confirmation ->
            // Decoupled SCA or polling: confirm to proceed
            client.confirmAccounts(accountsTicket, input.context)
        is DialogInput.Field ->
            // Text input required (e.g. TAN entry)
            client.respondAccounts(accountsTicket, input.context, userInput)
        is DialogInput.Selection ->
            // Pick one option (e.g. TAN method)
            client.respondAccounts(accountsTicket, input.context, selectedKey)
    }
    is Response.Redirect -> {
        // Send the user to r.redirect.url (browser or WebView), then confirm
        response = client.confirmAccounts(accountsTicket, r.redirect.context)
    }
    is Response.RedirectHandle -> {
        // Register a redirect URI to obtain the URL to send the user to
        val url = client.registerRedirectUri(
            accountsTicket, r.redirectHandle.handle, "myapp://callback",
        )
        // Send user to url, then confirm
        response = client.confirmAccounts(accountsTicket, r.redirectHandle.context)
    }
    is Response.Result -> Unit
}

// Extract the result
if (response is Response.Result) {
    // response.authenticated.jwt: authenticated data as a signed JSON Web Token.
    //   Verify the signature in a trusted environment before acting on the data.
    // response.session: short-lived, pass to consecutive service calls to speed up authentication
    // response.connectionData: persist alongside credentials to reuse the consent on
    //   subsequent calls (via Credentials.connectionData or in place of the credentials)
    //   or for non-interactive refreshes via RoutexRefreshClient. Pass
    //   `recurringConsents = true` on the service call to request a long-lived consent
    //   that skips the interrupt loop until it expires.
}
```

Subsequent interrupts are resolved by repeating the same `when` block until a `Response.Result` is returned.
Service methods throw [`RoutexError`](https://docs.yaxi.tech/errors.html) subclasses on a typed server error; the full per-service reference lives in the [documentation](https://docs.yaxi.tech).

## License

Apache-2.0

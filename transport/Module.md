# Module transport

HTTP transport abstraction shared by [routex-client][tech.yaxi.routex.client.RoutexClient] and [routex-refresh-client][tech.yaxi.routex.refresh.RoutexRefreshClient].

Pulled in transitively by both client modules.
The default transport is a Ktor engine selected per platform; consumers needing a proxy, custom timeouts, mTLS, or test fakes can implement [HttpTransport][tech.yaxi.routex.transport.HttpTransport] and pass it to the client constructor.

# Package tech.yaxi.routex.transport

Pluggable HTTP transport: the [HttpTransport][tech.yaxi.routex.transport.HttpTransport] interface, the [HttpRequest][tech.yaxi.routex.transport.HttpRequest] / [HttpResponse][tech.yaxi.routex.transport.HttpResponse] data classes, and the [HttpError][tech.yaxi.routex.transport.HttpError] family.

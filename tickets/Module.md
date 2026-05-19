# Module tickets

Typed ticket issuer for the YAXI routex services.

Issuing a ticket requires the API key secret, so the issuer belongs in a trusted backend in the vast majority of deployments.
Frontend issuance is possible and a handful of legitimate use cases exist, but only consider it if you understand the security implications of co-locating the API key secret with untrusted code; a leaked secret lets third parties issue tickets at your cost.

Backends that already use [routex-refresh-client][tech.yaxi.routex.refresh.RoutexRefreshClient] get the issuer transitively on their classpath.
Standalone backends that issue tickets for a frontend that calls [routex-client][tech.yaxi.routex.client.RoutexClient] should add `tech.yaxi:routex-tickets` directly.

# Package tech.yaxi.routex.tickets

YAXI routex ticket issuance: [RoutexTicketIssuer][tech.yaxi.routex.tickets.RoutexTicketIssuer] with one method per service.

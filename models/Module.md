# Module models

DTOs shared by [routex-client][tech.yaxi.routex.client.RoutexClient] and [routex-refresh-client][tech.yaxi.routex.refresh.RoutexRefreshClient].

Pulled in transitively by both client modules.
Direct consumers normally only touch the input/output types named on those clients' method signatures (for example [Ticket][tech.yaxi.routex.models.Ticket], [Credentials][tech.yaxi.routex.models.Credentials], [Response][tech.yaxi.routex.models.Response], [NonInteractiveResponse][tech.yaxi.routex.models.NonInteractiveResponse], [RoutexError][tech.yaxi.routex.models.RoutexError]).

# Package tech.yaxi.routex.models

YAXI Open Banking wire DTOs (services, tickets, credentials, dialogs, responses, errors).

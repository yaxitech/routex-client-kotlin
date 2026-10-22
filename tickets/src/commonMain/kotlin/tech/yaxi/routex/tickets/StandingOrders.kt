package tech.yaxi.routex.tickets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Ticket

@Serializable
internal data class StandingOrdersTicketData(
    val account: AccountReference,
)

internal class StandingOrdersService(
    data: StandingOrdersTicketData,
) : Service<Ticket.StandingOrders>(
        "StandingOrders",
        Json.encodeToJsonElement(StandingOrdersTicketData.serializer(), data),
        Ticket::StandingOrders,
    )

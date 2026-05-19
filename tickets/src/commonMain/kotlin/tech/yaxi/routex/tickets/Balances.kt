package tech.yaxi.routex.tickets

import tech.yaxi.routex.models.Ticket

internal object BalancesService : Service<Ticket.Balances>("Balances", null, Ticket::Balances)

package tech.yaxi.routex.tickets

import tech.yaxi.routex.models.Ticket

internal object AccountsService : Service<Ticket.Accounts>("Accounts", null, Ticket::Accounts)

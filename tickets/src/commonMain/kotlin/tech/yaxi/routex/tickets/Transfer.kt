package tech.yaxi.routex.tickets

import tech.yaxi.routex.models.Ticket

internal object TransferService : Service<Ticket.Transfer>("Transfer", null, Ticket::Transfer)

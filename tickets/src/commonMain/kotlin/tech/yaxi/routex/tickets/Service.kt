package tech.yaxi.routex.tickets

import kotlinx.serialization.json.JsonElement
import tech.yaxi.routex.models.Ticket

internal sealed class Service<T : Ticket>(
    val id: String,
    val data: JsonElement?,
    private val factory: (String) -> T,
) {
    fun ticket(raw: String): T = factory(raw)
}

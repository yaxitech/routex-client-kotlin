package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/** Payload returned by the transfer service. */
@ConsistentCopyVisibility
@Serializable
public data class Transfer internal constructor(
    public val status: PaymentStatus? = null,
)

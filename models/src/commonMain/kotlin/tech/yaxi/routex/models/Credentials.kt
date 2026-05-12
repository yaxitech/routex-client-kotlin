package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/**
 * User credentials for a given service connection.
 *
 * Which fields are required depends on the connection's [CredentialsModel]; the service
 * may still interact with the user (dialog / redirect) even when credentials are provided.
 *
 * @property connectionId Service connection to authenticate against.
 * @property userId User identifier, e.g. login name or online-banking PIN.
 * @property password User secret, e.g. online-banking password or PIN.
 * @property connectionData Opaque data from a previous successful flow, used to reuse an
 *   established consent without re-prompting the user.
 */
@Serializable
public data class Credentials
    @JvmOverloads
    constructor(
        public val connectionId: ConnectionId,
        public val userId: String? = null,
        public val password: String? = null,
        public val connectionData: ConnectionData? = null,
    )

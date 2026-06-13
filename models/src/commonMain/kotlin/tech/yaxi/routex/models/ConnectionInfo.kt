package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/**
 * Requirements for user identifier and password a connection accepts.
 *
 * @property full A full set of credentials may be provided to support fully embedded
 *   authentication (including scraped redirects).
 * @property userId Only a user identifier without a password may be provided. This is
 *   typically the case for decoupled authentication where the user e.g. authorizes
 *   access in a mobile application. Note that if password-less authentication fails
 *   (e.g. as no device for decoupled authentication is set up for the user and a
 *   redirect is not supported), an error is returned and the transaction has to be
 *   restarted with a full set of credentials.
 * @property none Credentials are not required. The user will provide them to the
 *   service provider during a redirect.
 */
@ConsistentCopyVisibility
@Serializable
public data class CredentialsModel internal constructor(
    public val full: Boolean,
    public val userId: Boolean,
    public val none: Boolean,
)

/**
 * Metadata describing a single service connection (bank or other provider integration).
 *
 * @property countries ISO 3166-1 ALPHA-2 country codes the connection covers.
 * @property displayName Display name of the provider.
 * @property credentials Credentials model the connection accepts.
 * @property userId Human-friendly label for the user identifier, when relevant.
 * @property password Human-friendly label for the PIN / password, when relevant.
 * @property advice Advice for the credentials to be displayed to the user.
 * @property logoId Logo identifier (resolve via the YAXI logo service).
 * @property bics ISO 20022 BICFIIdentifiers. Only populated when explicitly requested
 *   as part of a search.
 * @property bankCodes National bank codes (as used in IBANs). Only populated when
 *   explicitly requested as part of a search.
 */
@ConsistentCopyVisibility
@Serializable
public data class ConnectionInfo internal constructor(
    public val id: ConnectionId,
    public val countries: List<CountryCode>,
    public val displayName: String,
    public val credentials: CredentialsModel,
    public val userId: String? = null,
    public val password: String? = null,
    public val advice: String? = null,
    public val logoId: String,
    public val bics: List<String>? = null,
    public val bankCodes: List<String>? = null,
)

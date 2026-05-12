package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/**
 * Payment account exposed by a service connection.
 *
 * All fields are optional; which ones are populated depends on the ASPSP and on the
 * [AccountField]s requested when invoking the accounts service.
 *
 * @property iban ISO 20022 IBAN2007Identifier.
 * @property number Account number that is not an IBAN, for example an ISO 20022
 *   BBANIdentifier or the primary account number (PAN) of a card account.
 * @property bic ISO 20022 BICFIIdentifier.
 * @property bankCode National bank code.
 * @property currency ISO 4217 Alpha 3 currency code.
 * @property name Name of the account, assigned by the ASPSP.
 * @property displayName Display name of the account, assigned by the PSU.
 * @property ownerName Legal account owner.
 * @property productName Product name.
 * @property status Account status.
 * @property type Account type.
 */
@ConsistentCopyVisibility
@Serializable
public data class Account internal constructor(
    public val iban: String? = null,
    public val number: String? = null,
    public val bic: String? = null,
    public val bankCode: String? = null,
    public val currency: String? = null,
    public val name: String? = null,
    public val displayName: String? = null,
    public val ownerName: String? = null,
    public val productName: String? = null,
    public val status: AccountStatus? = null,
    public val type: AccountType? = null,
) {
    public companion object {
        /**
         * Build an [AccountFilter.Supports] that keeps accounts the provider lists as
         * potentially supporting [service].
         */
        @JvmStatic
        public fun supports(service: SupportedService): AccountFilter = AccountFilter.Supports(service)
    }
}

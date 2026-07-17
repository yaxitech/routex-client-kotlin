package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/**
 * Payload returned by the collect-payment service.
 *
 * @property status Provider-side payment status (when known).
 * @property debtorName Plaintext debtor name as reported by the provider.
 * @property debtorIban Plaintext debtor IBAN as reported by the provider.
 * @property encryptedDebtorName Encrypted debtor name as reported by the provider.
 * @property encryptedDebtorIban Encrypted debtor IBAN as reported by the provider.
 */
@Serializable
public class PaymentInitiation internal constructor(
    public val status: PaymentStatus? = null,
    public val debtorName: String? = null,
    public val debtorIban: String? = null,
    @Serializable(with = Base64ByteArraySerializer::class)
    public val encryptedDebtorName: ByteArray? = null,
    @Serializable(with = Base64ByteArraySerializer::class)
    public val encryptedDebtorIban: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is PaymentInitiation &&
            status == other.status &&
            debtorName == other.debtorName &&
            (encryptedDebtorName?.contentEquals(other.encryptedDebtorName) ?: (other.encryptedDebtorName == null)) &&
            debtorIban == other.debtorIban &&
            (encryptedDebtorIban?.contentEquals(other.encryptedDebtorIban) ?: (other.encryptedDebtorIban == null))

    override fun hashCode(): Int {
        var result = status?.hashCode() ?: 0
        result = 31 * result + (debtorName?.hashCode() ?: 0)
        result = 31 * result + (debtorIban?.hashCode() ?: 0)
        result = 31 * result + (encryptedDebtorName?.contentHashCode() ?: 0)
        result = 31 * result + (encryptedDebtorIban?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "PaymentInitiation(status=$status, debtorName=$debtorName, debtorIban=$debtorIban, " +
            "encryptedDebtorName=${encryptedDebtorName?.let { "<${it.size} bytes>" }}, " +
            "encryptedDebtorIban=${encryptedDebtorIban?.let { "<${it.size} bytes>" }})"
}

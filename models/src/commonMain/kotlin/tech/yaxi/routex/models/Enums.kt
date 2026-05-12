package tech.yaxi.routex.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Status of an [Account]. */
@Serializable
public enum class AccountStatus {
    @SerialName("Available")
    AVAILABLE,

    @SerialName("Terminated")
    TERMINATED,

    @SerialName("Blocked")
    BLOCKED,
}

/** Type of an [Account] (mostly aligned with ISO 20022 ExternalCashAccountType1Code). */
@Serializable
public enum class AccountType {
    /** Account used to post debits and credits (ISO 20022 CACC). */
    @SerialName("Current")
    CURRENT,

    /** Account used for credit card payments (ISO 20022 CARD). */
    @SerialName("Card")
    CARD,

    /** Account used for savings (ISO 20022 SVGS). */
    @SerialName("Savings")
    SAVINGS,

    /** Account used for call money (no dedicated ISO 20022 code; falls into SVGS). */
    @SerialName("CallMoney")
    CALL_MONEY,

    /** Account used for time deposits (no dedicated ISO 20022 code; falls into SVGS). */
    @SerialName("TimeDeposit")
    TIME_DEPOSIT,

    /** Account used for loans (ISO 20022 LOAN). */
    @SerialName("Loan")
    LOAN,

    @SerialName("Securities")
    SECURITIES,

    @SerialName("Insurance")
    INSURANCE,

    @SerialName("Commerce")
    COMMERCE,

    @SerialName("Rewards")
    REWARDS,
}

/** Kind of balance reported for an account. */
@Serializable
public enum class BalanceType {
    /** Balance from booked transactions. */
    @SerialName("Booked")
    BOOKED,

    /** Balance from booked transactions and pending debits. */
    @SerialName("Available")
    AVAILABLE,

    /** Expected balance from booked and pending transactions. */
    @SerialName("Expected")
    EXPECTED,
}

/** Who bears the charges for a transfer. */
@Serializable
public enum class ChargeBearer {
    @SerialName("DEBT")
    BORNE_BY_DEBTOR,

    @SerialName("CRED")
    BORNE_BY_CREDITOR,

    @SerialName("SHAR")
    SHARED,

    @SerialName("SLEV")
    FOLLOWING_SERVICE_LEVEL,
}

/** Type of connections to consider when searching. */
@Serializable
public enum class ConnectionType {
    /** Production connections. */
    @SerialName("Production")
    PRODUCTION,

    /** Sandbox connections, especially test systems provided by third parties. */
    @SerialName("Sandboxes")
    SANDBOXES,
}

/**
 * Context of a user dialog, distinguishing what the dialog is asking the user about.
 */
@Serializable
public enum class DialogContext {
    /**
     * SCA or TAN process. Several concrete cases are distinguished by the contained
     * [DialogInput]: [DialogInput.Confirmation] for decoupled processes (e.g. SCA app),
     * [DialogInput.Selection] for TAN method selection, [DialogInput.Field] for TAN entry.
     */
    @SerialName("Sca")
    SCA,

    /**
     * Account selection. A [DialogInput.Selection] gets returned with this context when an
     * account must be selected. Note that there might be just a single option that may be
     * chosen automatically without user interaction.
     */
    @SerialName("Accounts")
    ACCOUNTS,

    /**
     * Pending redirect confirmation. A [DialogInput.Confirmation] gets returned with this
     * context when a redirect got confirmed but no result is known yet.
     */
    @SerialName("Redirect")
    REDIRECT,

    /**
     * Pending SCT Inst payment. A [DialogInput.Confirmation] gets returned with this
     * context when an SCT Inst payment has been initialized and has not reached the final
     * status yet.
     */
    @SerialName("PaymentStatus")
    PAYMENT_STATUS,

    /**
     * Verification of Payee confirmation. A [DialogInput.Confirmation] gets returned with
     * this context when an explicit confirmation of the creditor is required due to a name
     * mismatch. Note that this confirmation has legal implications, releasing the bank
     * from liabilities in case of a transfer to an unintended receiver due to incorrect
     * creditor data.
     */
    @SerialName("VopConfirmation")
    VOP_CONFIRMATION,

    /**
     * Pending Verification of Payee check. A [DialogInput.Confirmation] gets returned with
     * this context when a Verification of Payee check is still pending.
     */
    @SerialName("VopCheck")
    VOP_CHECK,
}

/**
 * Type of an input field. May be used for showing hints or dedicated keyboard layouts and
 * for applying input restrictions or validation.
 */
@Serializable
public enum class InputType {
    @SerialName("Date")
    DATE,

    @SerialName("Email")
    EMAIL,

    @SerialName("Number")
    NUMBER,

    @SerialName("Phone")
    PHONE,

    @SerialName("Text")
    TEXT,
}

/** Reason why a payment failed. */
@Serializable
public enum class PaymentErrorCode {
    @SerialName("LimitExceeded")
    LIMIT_EXCEEDED,

    @SerialName("InsufficientFunds")
    INSUFFICIENT_FUNDS,
}

/** Payment product used when initiating a transfer. */
@Serializable
public enum class PaymentProduct {
    /** SEPA Credit Transfer (SCT) in EUR. */
    @SerialName("SepaCreditTransfer")
    SEPA_CREDIT_TRANSFER,

    /** SEPA Instant Credit Transfer (SCT Inst) in EUR. */
    @SerialName("SepaInstantCreditTransfer")
    SEPA_INSTANT_CREDIT_TRANSFER,

    /**
     * Default SEPA Credit Transfer in EUR. Tries SCT Inst with a fallback to SCT if this
     * is supported. Otherwise, SCT is used.
     */
    @SerialName("DefaultSepaCreditTransfer")
    DEFAULT_SEPA_CREDIT_TRANSFER,

    /** International credit transfer outside of SEPA (typically SWIFT). */
    @SerialName("CrossBorderCreditTransfer")
    CROSS_BORDER_CREDIT_TRANSFER,

    /** Domestic credit transfer in the domestic, non-EUR currency. */
    @SerialName("DomesticCreditTransfer")
    DOMESTIC_CREDIT_TRANSFER,

    /** Instant domestic credit transfer in the domestic, non-EUR currency. */
    @SerialName("DomesticInstantCreditTransfer")
    DOMESTIC_INSTANT_CREDIT_TRANSFER,
}

/** Progress status of a payment initiated via a service. */
@Serializable
public enum class PaymentStatus {
    /**
     * The payment was received and is being processed.
     *
     * Especially without realtime bookings this is often the final status reported by the
     * ASPSP.
     */
    @SerialName("Accepted")
    ACCEPTED,

    /**
     * The payment was partially accepted, i.e. only some of the transactions of a bulk
     * payment or the payment needs further authorization.
     */
    @SerialName("PartiallyAccepted")
    PARTIALLY_ACCEPTED,

    /** Settlement on the debtor's account has been completed. */
    @SerialName("CompletedDebtor")
    COMPLETED_DEBTOR,

    /** Settlement on the creditor's account has been completed. */
    @SerialName("CompletedCreditor")
    COMPLETED_CREDITOR,
}

/** Transient condition on the provider side. */
@Serializable
public enum class ProviderErrorCode {
    @SerialName("Maintenance")
    MAINTENANCE,
}

/** Level of secrecy for an input field. */
@Serializable
public enum class SecrecyLevel {
    /** The data is not a secret. */
    @SerialName("Plain")
    PLAIN,

    /**
     * The data is a one-time password. Usually this can be treated as no secret, but the
     * implementer might still choose to mask the input.
     */
    @SerialName("Otp")
    OTP,

    /** The data is a secret password. Input must be masked. */
    @SerialName("Password")
    PASSWORD,
}

/** Reason why a service is blocked for the user. */
@Serializable
public enum class ServiceBlockedCode {
    /** Something is not set up for the user, e.g. there are no TAN methods. */
    @SerialName("MissingSetup")
    MISSING_SETUP,

    /**
     * User attention is required via another channel. Typically the user needs to log
     * into Online Banking.
     */
    @SerialName("ActionRequired")
    ACTION_REQUIRED,
}

/** Services an account can be filtered by for potential support. */
@Serializable
public enum class SupportedService {
    @SerialName("CollectPayment")
    COLLECT_PAYMENT,
}

/** Reason why a ticket was rejected. */
@Serializable
public enum class TicketErrorCode {
    /** The `yaxi-ticket` header is missing. */
    @SerialName("Missing")
    MISSING,

    /** The ticket is invalid. */
    @SerialName("Invalid")
    INVALID,

    /** The ticket token lacks a key identifier (`kid`). */
    @SerialName("MissingKey")
    MISSING_KEY,

    /** The ticket references an unknown key. */
    @SerialName("UnknownKey")
    UNKNOWN_KEY,

    /** The ticket does not match the invoked service. */
    @SerialName("Mismatch")
    MISMATCH,

    /** The ticket is expired. */
    @SerialName("Expired")
    EXPIRED,

    /** The ticket lifetime is too long. */
    @SerialName("InvalidLifetime")
    INVALID_LIFETIME,

    /** The signing key is expired. */
    @SerialName("ExpiredKey")
    EXPIRED_KEY,

    /** The environment of the key does not match the environment of the service. */
    @SerialName("KeyEnvironmentMismatch")
    KEY_ENVIRONMENT_MISMATCH,
}

/** Reason why a payment product is not supported for a given request. */
@Serializable
public enum class UnsupportedProductReason {
    /** The amount is not allowed for the payment product. */
    @SerialName("Limit")
    LIMIT,

    /** The recipient is not capable of receiving the payment product. */
    @SerialName("Recipient")
    RECIPIENT,

    /** Scheduled payments are not supported. */
    @SerialName("Scheduled")
    SCHEDULED,
}

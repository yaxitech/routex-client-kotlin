package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Base class for all typed errors returned by the YAXI Open Banking services.
 *
 * Any [RoutexError] leaves the service call in a failed state: the flow cannot be
 * continued via `respond*` / `confirm*`, so recover by issuing a fresh
 * [Ticket][tech.yaxi.routex.models.Ticket] and starting again. See
 * [Handling errors](https://docs.yaxi.tech/errors.html) for per-variant guidance.
 *
 * Decode raw error responses via [RoutexErrorDispatcher.dispatch]; it returns one
 * of the concrete subclasses or [HttpResponse] / [NotFound] as a fallback.
 *
 * If YAXI ships a new error variant before this SDK is updated,
 * [RoutexErrorDispatcher.dispatch] surfaces it as [HttpResponse] rather than a
 * typed subclass. Always handle that fallback (use `else` in your `when` blocks).
 */
public sealed class RoutexError : RuntimeException() {
    abstract override val message: String?
}

/**
 * The bank or another upstream returned a response YAXI could not process. For
 * payment-style services this does not guarantee the operation failed: the
 * transaction may still have executed. Show
 * [userMessage][tech.yaxi.routex.models.UnexpectedError.userMessage] if present,
 * then consider prompting the user to share a diagnostics archive with YAXI.
 *
 * @property userMessage Text suitable for display to the user.
 */
@ConsistentCopyVisibility
@Serializable
public data class UnexpectedError internal constructor(
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * The user canceled the flow (rejecting an SCA prompt or letting it time out).
 * Ask whether the user wants to start again.
 */
@Serializable
public data object Canceled : RoutexError() {
    override val message: String? get() = null
}

/**
 * The bank rejected the supplied user identifier, password, or TAN input. Show
 * [userMessage][tech.yaxi.routex.models.InvalidCredentials.userMessage] if
 * present and let the user re-enter their credentials.
 *
 * @property userMessage Text suitable for display to the user.
 */
@ConsistentCopyVisibility
@Serializable
public data class InvalidCredentials internal constructor(
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * The bank blocked the service for this user. Common causes: the user must
 * accept new terms, change their password, or the account itself is blocked.
 * Show [userMessage][tech.yaxi.routex.models.ServiceBlocked.userMessage] if
 * present; otherwise pick a generic message based on
 * [code][tech.yaxi.routex.models.ServiceBlocked.code].
 *
 * @property code More specific machine-readable reason, when known.
 * @property userMessage Text suitable for display to the user.
 */
@ConsistentCopyVisibility
@Serializable
public data class ServiceBlocked internal constructor(
    public val code: ServiceBlockedCode? = null,
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * The bank refused the recurring consent attached to the call's [ConnectionData].
 * The YAXI service raises this only when the call carries
 * [Credentials.connectionData][tech.yaxi.routex.models.Credentials.connectionData].
 * Restart the flow without [Credentials.connectionData] so the user can grant a
 * fresh consent.
 */
@ConsistentCopyVisibility
@Serializable
public data class Unauthorized internal constructor(
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * The bank rejected the request for exceeding the consent's permitted access
 * frequency.
 *
 * This most commonly arises on the non-interactive refresh service, where a caller
 * exceeds the bank's limit on requests made without a user in session. Pass a
 * [UserInSession] to lift that limit.
 */
@ConsistentCopyVisibility
@Serializable
public data class AccessExceeded internal constructor(
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * Requested time period falls outside what the bank supports. Show
 * [userMessage][tech.yaxi.routex.models.PeriodOutOfBounds.userMessage] if
 * present. The
 * [transactions][tech.yaxi.routex.client.RoutexClient.transactions] flow raises
 * this.
 *
 * @property userMessage Text suitable for display to the user.
 */
@ConsistentCopyVisibility
@Serializable
public data class PeriodOutOfBounds internal constructor(
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * The bank cannot perform the requested operation for this combination of
 * inputs. Examples: an instant payment was requested but the bank only supports
 * the standard variant, or the connection does not support the chosen service.
 * See
 * [Instant by default](https://docs.yaxi.tech/collect-payment.html#_instant_by_default).
 * Show [userMessage][tech.yaxi.routex.models.UnsupportedProduct.userMessage] if
 * present.
 *
 * @property reason More specific machine-readable reason, when known.
 * @property userMessage Text suitable for display to the user.
 */
@ConsistentCopyVisibility
@Serializable
public data class UnsupportedProduct internal constructor(
    public val reason: UnsupportedProductReason? = null,
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * The bank rejected the payment (for example, an exceeded transfer limit). Show
 * [userMessage][tech.yaxi.routex.models.PaymentFailed.userMessage] if present;
 * [code][tech.yaxi.routex.models.PaymentFailed.code] carries a machine-readable
 * reason. Specific to
 * [collectPayment][tech.yaxi.routex.client.RoutexClient.collectPayment].
 *
 * @property code More specific machine-readable reason, when known.
 * @property userMessage Text suitable for display to the user.
 */
@ConsistentCopyVisibility
@Serializable
public data class PaymentFailed internal constructor(
    public val code: PaymentErrorCode? = null,
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * A value passed to the call was malformed (for example, an invalid IBAN). The
 * [error][tech.yaxi.routex.models.UnexpectedValue.error] field explains what was
 * wrong. This error targets the application developer: validate inputs before
 * submitting them, especially when they came from the user.
 *
 * @property error Human-readable description of the malformed value.
 */
@ConsistentCopyVisibility
@Serializable
public data class UnexpectedValue internal constructor(
    public val error: String,
) : RoutexError() {
    override val message: String get() = error
}

/**
 * The routex service rejected the [Ticket][tech.yaxi.routex.models.Ticket].
 * When [code][tech.yaxi.routex.models.TicketError.code] is
 * [TicketErrorCode.EXPIRED][tech.yaxi.routex.models.TicketErrorCode.EXPIRED],
 * the user took too long to finish the flow: issue a fresh ticket and start
 * over. Any other [code][tech.yaxi.routex.models.TicketError.code] indicates a
 * configuration error in the calling application (wrong key, environment
 * mismatch, ticket / service mismatch).
 *
 * @property error Human-readable description of the problem.
 * @property code Machine-readable reason code.
 */
@ConsistentCopyVisibility
@Serializable
public data class TicketError internal constructor(
    public val error: String,
    public val code: TicketErrorCode,
) : RoutexError() {
    override val message: String get() = error
}

/**
 * The bank reported a technical failure (often maintenance). For payment-style
 * services this does not guarantee the operation failed: the transaction may
 * still have executed. Tell the user a technical error occurred and to retry
 * later.
 */
@ConsistentCopyVisibility
@Serializable
public data class ProviderError internal constructor(
    public val code: ProviderErrorCode? = null,
    public val userMessage: String? = null,
) : RoutexError() {
    override val message: String? get() = userMessage
}

/**
 * The requested resource was not found. On
 * [transactions][tech.yaxi.routex.client.RoutexClient.transactions] this
 * typically means the supplied [AccountReference] is unknown to the bank: verify
 * the reference. [info][tech.yaxi.routex.client.RoutexClient.info] and
 * [trace][tech.yaxi.routex.client.RoutexClient.trace] also raise this.
 */
public data object NotFound : RoutexError() {
    override val message: String? get() = null
}

/**
 * The service call requires user interaction to complete.
 *
 * Only the non-interactive
 * [RoutexRefreshClient][tech.yaxi.routex.refresh.RoutexRefreshClient] endpoints
 * raise this error. The interactive
 * [RoutexClient][tech.yaxi.routex.client.RoutexClient] surfaces user-interaction
 * needs via [Response.Dialog], [Response.Redirect], or [Response.RedirectHandle]
 * instead. Recover by running the user through a fresh interactive flow on
 * [RoutexClient][tech.yaxi.routex.client.RoutexClient] and persisting the new
 * [ConnectionData] for subsequent refreshes.
 */
@Serializable
public data object InterruptError : RoutexError() {
    override val message: String? get() = null
}

/**
 * Fallback error carrying the raw HTTP status and response body. Returned by
 * [RoutexErrorDispatcher.dispatch] when no typed error variant matches; typically
 * when the YAXI service introduces a new error variant ahead of an updated SDK.
 * Display a generic technical-error message; report internally if it recurs.
 */
@ConsistentCopyVisibility
public data class HttpResponse internal constructor(
    public val status: Int,
    public val text: String,
) : RoutexError() {
    override val message: String get() = "HTTP $status: $text"
}

/**
 * Decodes wire-form error responses from the YAXI service into typed [RoutexError]
 * subclasses. Falls back to [NotFound] for HTTP 404 and to [HttpResponse] for any
 * status whose body the dispatcher cannot recognize as a known variant.
 */
public object RoutexErrorDispatcher {
    /**
     * Decode an error response.
     *
     * @param httpStatus HTTP status code returned by the YAXI service.
     * @param body Raw response body, possibly empty.
     */
    public fun dispatch(
        httpStatus: Int,
        body: String,
    ): RoutexError {
        val parsed: JsonElement? =
            try {
                if (body.isEmpty()) null else RoutexJson.parseToJsonElement(body)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        if (parsed is JsonObject && parsed.size == 1) {
            val (variant, payload) = parsed.entries.first()
            val variantError = runCatching { decodeVariant(variant, payload) }.getOrNull()
            if (variantError != null) return variantError
        }

        if (httpStatus == 404) return NotFound

        return HttpResponse(httpStatus, body)
    }

    private fun decodeVariant(
        variant: String,
        payload: JsonElement,
    ): RoutexError? {
        val serializer: KSerializer<out RoutexError> =
            when (variant) {
                "UnexpectedError" -> UnexpectedError.serializer()
                "Canceled" -> Canceled.serializer()
                "InvalidCredentials" -> InvalidCredentials.serializer()
                "ServiceBlocked" -> ServiceBlocked.serializer()
                "Unauthorized" -> Unauthorized.serializer()
                "AccessExceeded" -> AccessExceeded.serializer()
                "PeriodOutOfBounds" -> PeriodOutOfBounds.serializer()
                "UnsupportedProduct" -> UnsupportedProduct.serializer()
                "PaymentFailed" -> PaymentFailed.serializer()
                "UnexpectedValue" -> UnexpectedValue.serializer()
                "TicketError" -> TicketError.serializer()
                "ProviderError" -> ProviderError.serializer()
                "InterruptError" -> InterruptError.serializer()
                else -> return null
            }
        @Suppress("UNCHECKED_CAST")
        return RoutexJson.decodeFromJsonElement(serializer as KSerializer<RoutexError>, payload)
    }
}

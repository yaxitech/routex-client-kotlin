package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Wrapper around an authenticated YAXI service response carried as a JWT.
 *
 * Service results are signed with the same secret key your backend uses to mint
 * tickets. Signature verification is intentionally NOT performed client-side
 * because the secret never leaves your backend. Two paths:
 *
 *  - **Client-only display.** Calling [decodeUnverified] is acceptable when the
 *    decoded data is only used inside this client process (for example, rendered in a
 *    UI) and never leaves it.
 *  - **Anything else.** The client process must NOT decode and forward the values to
 *    a backend, persist them, or act on them in a security-sensitive way. Forward the
 *    raw [jwt] string to a backend that holds your YAXI API secret, have the backend
 *    verify the JWT signature and the ticket id, and only then act on the verified
 *    payload.
 *
 * Treat output of [decodeUnverified] as untrusted user input the moment it leaves this
 * process. See [Verify results](https://docs.yaxi.tech/verify.html) for backend
 * verification snippets.
 */
public class Authenticated<T> internal constructor(
    private val rawJwt: String,
    private val parser: (String) -> T,
) {
    /**
     * The original JWT string. Forward to a backend that holds your YAXI API secret
     * (the same key used to mint tickets); verify the signature there and act on the
     * verified payload server-side.
     */
    public val jwt: String get() = rawJwt

    /**
     * Decode the JWT body's `data` claim into [T] **without verifying the signature.**
     *
     * Use only for client-only display. NEVER decode the JWT here and send the
     * resulting values to a backend or use them for any security-sensitive decision.
     * Forward [jwt] to a backend, verify it there with your YAXI API secret, and use
     * the verified payload from the backend instead. See
     * [Verify results](https://docs.yaxi.tech/verify.html) for backend snippets.
     *
     * @throws AuthenticatedDecodeError if the JWT cannot be decoded.
     */
    @Throws(AuthenticatedDecodeError::class)
    public fun decodeUnverified(): T = parser(rawJwt)
}

/**
 * Thrown when a JWT-bearing response cannot be decoded, the JWT is structurally
 * malformed, its body is not valid JSON, the `data` claim is missing, or the claim's
 * value does not match the expected typed payload shape.
 */
public class AuthenticatedDecodeError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal fun <T> decodeJwtDataClaim(
    jwt: String,
    serializer: KSerializer<T>,
): T {
    val segments = jwt.split(".")
    if (segments.size < 2) {
        throw AuthenticatedDecodeError("JWT must contain at least two segments")
    }
    val payloadText =
        try {
            Base64UrlNoPad.decode(segments[1]).decodeToString()
        } catch (e: IllegalArgumentException) {
            throw AuthenticatedDecodeError("JWT payload is not valid base64url: ${e.message}", e)
        }
    val payload =
        try {
            authenticatedJson.parseToJsonElement(payloadText).jsonObject
        } catch (e: SerializationException) {
            throw AuthenticatedDecodeError("JWT payload is not a JSON object: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw AuthenticatedDecodeError("JWT payload is not a JSON object: ${e.message}", e)
        }
    val data =
        payload["data"] ?: throw AuthenticatedDecodeError("JWT payload missing `data` claim")
    return try {
        authenticatedJson.decodeFromJsonElement(serializer, data)
    } catch (e: SerializationException) {
        throw AuthenticatedDecodeError("JWT `data` claim does not match expected shape: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw AuthenticatedDecodeError("JWT `data` claim does not match expected shape: ${e.message}", e)
    }
}

private val authenticatedJson: Json = Json { ignoreUnknownKeys = true }

package tech.yaxi.routex.client

import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import tech.yaxi.routex.models.Base64UrlNoPad

/**
 * Pure-Kotlin HS256 JWT signer backed by KotlinCrypto's `hmac-sha2` primitive. Emits the
 * compact JWT serialization `<headerB64Url>.<payloadB64Url>.<signatureB64Url>` with a
 * fixed header of `{"alg":"HS256","typ":"JWT","kid":"<keyId>"}`.
 *
 * Only used by the online-test suites to mint short-lived tickets against the live API;
 * production code does not sign JWTs on the client side.
 */
internal object HS256 {
    /**
     * Sign [claimsJson] with HS256 and the HMAC key [secret], returning the full compact
     * JWT. [keyId] is embedded as the `kid` header parameter so the server can pick the
     * right verifying key.
     */
    fun sign(
        keyId: String,
        secret: ByteArray,
        claimsJson: String,
    ): String {
        val header =
            Base64UrlNoPad.encode(
                """{"alg":"HS256","typ":"JWT","kid":"$keyId"}""".encodeToByteArray(),
            )
        val payload = Base64UrlNoPad.encode(claimsJson.encodeToByteArray())
        val signingInput = "$header.$payload"
        val mac = HmacSHA256(secret)
        mac.update(signingInput.encodeToByteArray())
        val signature = Base64UrlNoPad.encode(mac.doFinal())
        return "$signingInput.$signature"
    }
}

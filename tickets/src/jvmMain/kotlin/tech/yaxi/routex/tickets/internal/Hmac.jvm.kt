package tech.yaxi.routex.tickets.internal

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal actual fun hmacSha256(
    key: ByteArray,
    message: ByteArray,
): ByteArray {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
    return mac.doFinal(message)
}

private const val HMAC_ALGORITHM: String = "HmacSHA256"

@file:OptIn(ExperimentalForeignApi::class)

package tech.yaxi.routex.tickets.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

internal actual fun hmacSha256(
    key: ByteArray,
    message: ByteArray,
): ByteArray {
    require(key.isNotEmpty()) { "empty HMAC key" }
    val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
    out.usePinned { outPinned ->
        key.usePinned { keyPinned ->
            message.usePinned { messagePinned ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyPinned.addressOf(0),
                    key.size.convert(),
                    if (message.isEmpty()) null else messagePinned.addressOf(0),
                    message.size.convert(),
                    outPinned.addressOf(0),
                )
            }
        }
    }
    return out
}

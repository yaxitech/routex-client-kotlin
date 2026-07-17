package tech.yaxi.routex.tickets.internal

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

@JsModule("@noble/hashes/sha2.js")
private external object NobleSha2 {
    val sha256: dynamic
}

@JsModule("@noble/hashes/hmac.js")
private external object NobleHmac {
    fun hmac(
        hash: dynamic,
        key: Uint8Array,
        message: Uint8Array,
    ): Uint8Array
}

private fun ByteArray.toUint8Array(): Uint8Array {
    val i8 = unsafeCast<Int8Array>()
    return Uint8Array(i8.buffer, i8.byteOffset, i8.length)
}

private fun Uint8Array.toByteArray(): ByteArray = Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>()

internal actual fun hmacSha256(
    key: ByteArray,
    message: ByteArray,
): ByteArray = NobleHmac.hmac(NobleSha2.sha256, key.toUint8Array(), message.toUint8Array()).toByteArray()

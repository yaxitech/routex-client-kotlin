package tech.yaxi.routex.settlement.internal

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.posix.memcpy

// Confines the cinterop calling conventions; the actuals deal in ByteArray
// and exceptions exclusively.

/** A facade call signalled failure through the Objective-C error convention. */
internal class AppleInteropException(
    error: NSError?,
) : RuntimeException(error?.localizedDescription ?: "apple-interop call failed")

/**
 * Run a facade call that reports failure through a trailing `NSError**`
 * parameter; `null` results turn into [AppleInteropException].
 */
internal inline fun <T : Any> interop(block: (CPointer<ObjCObjectVar<NSError?>>) -> T?): T =
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        block(error.ptr) ?: throw AppleInteropException(error.value)
    }

/**
 * Run a facade call that reports failure as `false` plus a trailing
 * `NSError**` parameter (the Objective-C convention for `Void`-returning
 * throwing methods).
 */
internal inline fun interopCheck(block: (CPointer<ObjCObjectVar<NSError?>>) -> Boolean) {
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!block(error.ptr)) throw AppleInteropException(error.value)
    }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

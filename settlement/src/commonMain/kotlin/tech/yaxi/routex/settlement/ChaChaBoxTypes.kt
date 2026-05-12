package tech.yaxi.routex.settlement

internal class ChaChaBoxDecryptError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal sealed class ChaChaBoxKeys {
    abstract val publicKey: ChaChaBoxPublicKey

    class V1(
        val x25519Secret: ByteArray,
        val x25519Public: ByteArray,
    ) : ChaChaBoxKeys() {
        init {
            require(x25519Secret.size == X25519_KEY_LENGTH) {
                "X25519 secret key must be $X25519_KEY_LENGTH bytes"
            }
            require(x25519Public.size == X25519_KEY_LENGTH) {
                "X25519 public key must be $X25519_KEY_LENGTH bytes"
            }
        }

        override val publicKey: ChaChaBoxPublicKey
            get() = ChaChaBoxPublicKey.V1(x25519Public)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is V1) return false
            return x25519Secret.contentEquals(other.x25519Secret) &&
                x25519Public.contentEquals(other.x25519Public)
        }

        override fun hashCode(): Int = x25519Secret.contentHashCode() * 31 + x25519Public.contentHashCode()

        companion object {
            const val X25519_KEY_LENGTH: Int = 32
        }
    }
}

internal sealed class ChaChaBoxPublicKey {
    class V1(
        val x25519: ByteArray,
    ) : ChaChaBoxPublicKey() {
        init {
            require(x25519.size == ChaChaBoxKeys.V1.X25519_KEY_LENGTH) {
                "X25519 public key must be ${ChaChaBoxKeys.V1.X25519_KEY_LENGTH} bytes"
            }
        }

        override fun equals(other: Any?): Boolean = other is V1 && x25519.contentEquals(other.x25519)

        override fun hashCode(): Int = x25519.contentHashCode()
    }
}

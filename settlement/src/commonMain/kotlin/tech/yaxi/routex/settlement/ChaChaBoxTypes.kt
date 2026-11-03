package tech.yaxi.routex.settlement

import tech.yaxi.routex.settlement.internal.MlKem768KeyPair
import tech.yaxi.routex.settlement.internal.mlKem768IsValidEncapsulationKey
import tech.yaxi.routex.settlement.internal.mlKem768KeyPair

internal sealed class ChaChaBoxError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** The box starts with [version], which the recipient key does not accept. */
    class UnsupportedVersion(
        val version: Byte,
    ) : ChaChaBoxError("unsupported version ${version.toUByte()}")

    class SealingFailed(
        cause: Throwable? = null,
    ) : ChaChaBoxError("could not seal", cause)

    class UnsealingFailed(
        cause: Throwable? = null,
    ) : ChaChaBoxError("could not unseal", cause)
}

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

    class V2(
        val x25519Secret: ByteArray,
        val x25519Public: ByteArray,
        val mlKemSeed: ByteArray,
    ) : ChaChaBoxKeys() {
        init {
            require(x25519Secret.size == V1.X25519_KEY_LENGTH) {
                "X25519 secret key must be ${V1.X25519_KEY_LENGTH} bytes"
            }
            require(x25519Public.size == V1.X25519_KEY_LENGTH) {
                "X25519 public key must be ${V1.X25519_KEY_LENGTH} bytes"
            }
            require(mlKemSeed.size == ML_KEM_SEED_LENGTH) {
                "ML-KEM seed must be $ML_KEM_SEED_LENGTH bytes"
            }
        }

        val mlKem: MlKem768KeyPair = mlKem768KeyPair(mlKemSeed)

        val mlKemPublic: ByteArray
            get() = mlKem.encapsulationKey

        override val publicKey: ChaChaBoxPublicKey.V2
            get() = ChaChaBoxPublicKey.V2(x25519Public, mlKemPublic)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is V2) return false
            return x25519Secret.contentEquals(other.x25519Secret) &&
                x25519Public.contentEquals(other.x25519Public) &&
                mlKemSeed.contentEquals(other.mlKemSeed)
        }

        override fun hashCode(): Int = arrayOf(x25519Secret, x25519Public, mlKemSeed).contentDeepHashCode()

        companion object {
            const val ML_KEM_SEED_LENGTH: Int = 64
            const val ML_KEM_PUBLIC_KEY_LENGTH: Int = 1184
        }
    }
}

internal sealed class ChaChaBoxPublicKey {
    /** Wire format version; boxes sealed to this key start with it. */
    abstract val version: Byte

    abstract fun toBytes(): ByteArray

    class V1(
        val x25519: ByteArray,
    ) : ChaChaBoxPublicKey() {
        init {
            require(x25519.size == ChaChaBoxKeys.V1.X25519_KEY_LENGTH) {
                "X25519 public key must be ${ChaChaBoxKeys.V1.X25519_KEY_LENGTH} bytes"
            }
        }

        override val version: Byte
            get() = VERSION

        override fun toBytes(): ByteArray = x25519.copyOf()

        override fun equals(other: Any?): Boolean = other is V1 && x25519.contentEquals(other.x25519)

        override fun hashCode(): Int = x25519.contentHashCode()

        companion object {
            const val VERSION: Byte = 0x01
        }
    }

    class V2(
        val x25519: ByteArray,
        val mlKem: ByteArray,
    ) : ChaChaBoxPublicKey() {
        init {
            require(x25519.size == ChaChaBoxKeys.V1.X25519_KEY_LENGTH) {
                "X25519 public key must be ${ChaChaBoxKeys.V1.X25519_KEY_LENGTH} bytes"
            }
            require(mlKem.size == ChaChaBoxKeys.V2.ML_KEM_PUBLIC_KEY_LENGTH) {
                "ML-KEM public key must be ${ChaChaBoxKeys.V2.ML_KEM_PUBLIC_KEY_LENGTH} bytes"
            }
        }

        override val version: Byte
            get() = VERSION

        override fun toBytes(): ByteArray = x25519 + mlKem

        override fun equals(other: Any?): Boolean =
            other is V2 && x25519.contentEquals(other.x25519) && mlKem.contentEquals(other.mlKem)

        override fun hashCode(): Int = x25519.contentHashCode() * 31 + mlKem.contentHashCode()

        companion object {
            const val VERSION: Byte = 0x02
        }
    }

    companion object {
        /** Decodes [bytes], whose length selects the version. */
        fun fromBytes(bytes: ByteArray): ChaChaBoxPublicKey {
            val x25519Length = ChaChaBoxKeys.V1.X25519_KEY_LENGTH
            return when (bytes.size) {
                x25519Length -> V1(bytes.copyOf())
                x25519Length + ChaChaBoxKeys.V2.ML_KEM_PUBLIC_KEY_LENGTH -> {
                    val mlKem = bytes.copyOfRange(x25519Length, bytes.size)
                    require(mlKem768IsValidEncapsulationKey(mlKem)) { "invalid ML-KEM public key" }
                    V2(bytes.copyOfRange(0, x25519Length), mlKem)
                }
                else -> throw IllegalArgumentException("public key has an invalid length of ${bytes.size} bytes")
            }
        }
    }
}

package tech.yaxi.routex.settlement

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import tech.yaxi.routex.settlement.internal.blake2bDigest
import tech.yaxi.routex.settlement.internal.chacha20Poly1305Encrypt
import tech.yaxi.routex.settlement.internal.hkdfBlake2b512
import tech.yaxi.routex.settlement.internal.mlKem768Encapsulate
import tech.yaxi.routex.settlement.internal.mlKem768IsAvailable
import tech.yaxi.routex.settlement.internal.x25519GenerateKeyPair
import tech.yaxi.routex.settlement.internal.x25519SharedSecret

/** Runs [block] only where ML-KEM-768 is available, i.e., not on Apple below OS 26. */
internal inline fun withMlKem(block: () -> Unit) {
    if (mlKem768IsAvailable()) block()
}

class ChaChaBoxTest {
    @Test
    fun generatesV2KeysWhereMlKemIsAvailable() {
        val keys = ChaChaBox.generateKeys()
        if (mlKem768IsAvailable()) {
            assertIs<ChaChaBoxKeys.V2>(keys)
        } else {
            assertIs<ChaChaBoxKeys.V1>(keys)
        }
    }

    @Test
    fun v2RoundTrips() =
        withMlKem {
            val keys = generateV2Keys()
            val sealed = ChaChaBox.seal(keys.publicKey, PLAINTEXT)
            assertEquals(2, sealed[0].toInt())
            assertEquals(1 + 1088 + 32 + PLAINTEXT.size + 16, sealed.size)
            assertContentEquals(PLAINTEXT, ChaChaBox.unseal(keys, sealed))
        }

    @Test
    fun v1RoundTrips() {
        val keys = generateV1Keys()
        val sealed = ChaChaBox.seal(keys.publicKey, PLAINTEXT)
        assertEquals(1, sealed[0].toInt())
        assertEquals(1 + 32 + PLAINTEXT.size + 16, sealed.size)
        assertContentEquals(PLAINTEXT, ChaChaBox.unseal(keys, sealed))
    }

    @Test
    fun unsealsV2Fixture() =
        withMlKem {
            val keys =
                v2Keys(
                    "B+l9rW+HZLDH4JMosa4Te7MytgQwWCwpTqHI16t2NSuLucdgAQtURG+Nx4+o1XXoGiYU9CY8S76a" +
                        "TzDAA+AzteZeMHkdu1alxqctHMTpmno0j1vzxj41VTKwQPYVVVXK",
                )
            val sealed =
                Base64.decode(
                    "Aj0NHElgG+2rEPMOmMURDY5Oy1D1jD1Q4u6Y1CMwCGbnkQiiAew8UThPc0IiBSQCeutrSshOIsLp" +
                        "0Ogid2CAQ1Ehmp+BIGX8DKPC97AoJeB1BcQzUOKFKrCouj2vanaq1Hzfxriu1jqmt1b5UZyNsU/w" +
                        "mMLG3rwTFzaDyHqe7JHf4suoQFq33ZPmB9h8VJo/4dBQ1czcS62q/78x0jnCZLLt1MUhibvxJJxM" +
                        "ocwbW8Dmo3vvoUr+qa7DXiZSD4CJ85BeaNvmtkQU02rXSClIFtSGGLz3JfELKDRlOfns9SoK6cTx" +
                        "xDe8BZzbSkWMLUL8bXhOICUR5zWLdiaut8UbrTqV6zWez8b6HXPi4aZ1YFdbrOCFRpYKsOjVHAht" +
                        "4jnFLkHV3PPl3zbUBxKnWKSg6PKHc4pRB1EPD8gmb10Kw9E7hzKJ+BjdivwBBw0U6wKI4jODRISK" +
                        "BmD9HF8XWfq4s3ck4IXWA4eaA8q+SFWPEmzu/Y5D5PSqfqBY3Dpk6IQTISbaFCmt/m7BAx6HGeIS" +
                        "TmO9D19hjpejOd309HKTV8xPRsrAcqHZESlKuqA8XAfvHvlh+aFPHPZHl0oD0wcUVpUW2AyiI0RM" +
                        "IArn1OPgG6V0FjwKPj1xAq63LxfyMTKWfxuHeRcNMQ95eKnZYhcRi6x7D/m7eXXpFeVw8neQA6jV" +
                        "qUW//hdsAESmspiXxFa58ZpXWDWjWJIwBhLbxvWpylo6Nq9SPYb+6OW8W46YM20Y0hvgn1pU+ZER" +
                        "ZhgWt5EoWfrwegQkOkiZyE9jToVO3Hh/uE7PIEmXES+NDKe2+g+Dg02gGKK/cWGsLkLzRBgJCNKI" +
                        "wyZxMHafWB2Jnpb5/AJ2YhkAkhDulABtt68svQcgfOzwuk3qPgNcJpIck/79xjeS0Y9D6zd1kmk+" +
                        "2faj3hvOi/O8hZS+1p6m2e3nr19rTwWWqj6f07/ACZJW0jRb/UtNyANiZ8NjhimV+9XM1ophZahQ" +
                        "gWm0tJ9Cu5VXjHF64PepYoaa7WUpD5NKAqxl1b7qeoUILvnymvoEr2hsp/DB081v/QqmYmK/aMxa" +
                        "76kNL2Jjfpc3p0HjdhkvNemWrJ11LYO/mq2yh7xqDEzCePbFVtwrJCiHJ1fOrvZlTZs9QVZStynf" +
                        "rCd4oIDqboGczY+YMpZ7F/quHakD30Tmatwe0nHO9iljeUgFeIGNMaqdOKOgrBsTXHXu0ldW8zQQ" +
                        "wrxUaDzeRgzrVPoorodk5pQL3eXNfcGtNOeJ8UYxDHqj+wlTuPWH3aTvKolCUjcf7nvnbOL8Bl50" +
                        "v+1oRtuD2C0SCcOb/qUKyVyLsbhOI0jRyAUn4O1h0MOiBGdSvOxN0N45czUAsYCe3Mq4zSa+1bpN" +
                        "ettaco3Gue1Qr8XiLbmzl7r+6IkPK/mokFbWvz1OxBMXQBhKL4fpo4l8t0bva1xmfhepdOTTAwlw" +
                        "rAFAUfPDKUBOoj6OeRnOZUfNEke/UUNwfnmcIJrGkxxP8uYMrWL7qjSKu+dofmISKptfaHpsgm9W" +
                        "RPvRtVJEUJsB",
                )
            assertContentEquals(PLAINTEXT, ChaChaBox.unseal(keys, sealed))
        }

    @Test
    fun unsealsV1Fixture() {
        val keys = v1Keys("NVHjrhN/ryOVoos4NLA5mfY1ydZQrd6T3MrnzoX/eY4=")
        val sealed =
            Base64.decode(
                "Ad3U1J+DNDGOo/162Iwh3kIfMDpYX7hIGdj6fx5o32B9g9Yvqtx7cV4qNX+rCDIInJfM2Zfmi850tlSQ6g==",
            )
        assertContentEquals(PLAINTEXT, ChaChaBox.unseal(keys, sealed))
    }

    @Test
    fun v1KeysRefuseLegacyBoxes() {
        // Without a version byte, the leading byte of the ephemeral key reads as the version.
        val keys = v1Keys("31eNwO39w4HcvZzmcUzKCq1uPHMP5FlazyVhl/K2u8g=")
        val sealed = Base64.decode("Rtu9M9NV4Y5bSPZoKAGMYfFHBnj1mRS+oNmUNDGuzhBTNvOErnAmUCCF+36G8KERMae/5FQ2jlOMYWHC")
        assertUnsupportedVersion(0x46) { ChaChaBox.unseal(keys, sealed) }

        // A legacy box whose ephemeral key starts with 0x01 does not open as a V1 box.
        val keys01 = v1Keys("oaVSp+ck/aCb1wxt8mbp2X/hMtTFnJ4OdkYIVVwVO2s=")
        val sealed01 = Base64.decode("YmperIJdpIjqb0dojgsK0oyNUaUvoMWI1ik6dqODZBpB3qi5Mn9UsysYJEnGtpEeeCA4vniJcg2ktEs=")
        assertFailsWith<ChaChaBoxError.UnsealingFailed> { ChaChaBox.unseal(keys01, byteArrayOf(0x01) + sealed01) }
    }

    @Test
    fun v2KeysRefuseV1Boxes() =
        withMlKem {
            val keys = generateV2Keys()
            val sealed = ChaChaBox.seal(ChaChaBoxPublicKey.V1(keys.x25519Public), PLAINTEXT)
            assertUnsupportedVersion(0x01) { ChaChaBox.unseal(keys, sealed) }
        }

    @Test
    fun v1KeysRefuseV2Boxes() =
        withMlKem {
            val sealed = ChaChaBox.seal(generateV2Keys().publicKey, PLAINTEXT)
            assertUnsupportedVersion(0x02) { ChaChaBox.unseal(generateV1Keys(), sealed) }
        }

    @Test
    fun rejectsTamperedV2Boxes() =
        withMlKem {
            val keys = generateV2Keys()
            val sealed = ChaChaBox.seal(keys.publicKey, PLAINTEXT)
            assertUnsupportedVersion(0x03) { ChaChaBox.unseal(keys, sealed.withBitFlipped(0)) }
            // ML-KEM ciphertext, ephemeral X25519 key, AEAD ciphertext, and tag.
            for (position in listOf(1, 1 + 1088, 1 + 1088 + 32, sealed.size - 1)) {
                assertFailsWith<ChaChaBoxError.UnsealingFailed>("position $position") {
                    ChaChaBox.unseal(keys, sealed.withBitFlipped(position))
                }
            }
        }

    @Test
    fun rejectsTruncatedBoxes() {
        val v1Keys = generateV1Keys()
        val v1Sealed = ChaChaBox.seal(v1Keys.publicKey, PLAINTEXT)
        for (length in listOf(0, 1, 4, 1 + 32, 1 + 32 + 15, v1Sealed.size - 1)) {
            assertFailsWith<ChaChaBoxError.UnsealingFailed>("length $length") {
                ChaChaBox.unseal(v1Keys, v1Sealed.copyOf(length))
            }
        }

        withMlKem {
            val v2Keys = generateV2Keys()
            val v2Sealed = ChaChaBox.seal(v2Keys.publicKey, PLAINTEXT)
            for (length in listOf(0, 1, 4, 1 + 1088, 1 + 1088 + 32, 1 + 1088 + 32 + 15, v2Sealed.size - 1)) {
                assertFailsWith<ChaChaBoxError.UnsealingFailed>("length $length") {
                    ChaChaBox.unseal(v2Keys, v2Sealed.copyOf(length))
                }
            }
            assertFailsWith<ChaChaBoxError.UnsealingFailed> { ChaChaBox.unseal(v2Keys, byteArrayOf(2, 1, 3, 3, 7)) }
            assertUnsupportedVersion(0x01) { ChaChaBox.unseal(v2Keys, byteArrayOf(1, 3, 3, 7)) }
        }
    }

    @Test
    fun rejectsLowOrderX25519Keys() {
        val zero = ByteArray(32)
        assertFailsWith<ChaChaBoxError.SealingFailed> { ChaChaBox.seal(ChaChaBoxPublicKey.V1(zero), PLAINTEXT) }

        // A box that would authenticate under the all-zero shared secret.
        val v1Keys = generateV1Keys()
        val v1Key = hkdfBlake2b512(ByteArray(32), ByteArray(0), zero + v1Keys.x25519Public, 32)
        val v1Nonce = blake2bDigest(zero + v1Keys.x25519Public, 12)
        val v1Box = byteArrayOf(0x01) + zero + chacha20Poly1305Encrypt(v1Key, v1Nonce, PLAINTEXT)
        assertFailsWith<ChaChaBoxError.UnsealingFailed> { ChaChaBox.unseal(v1Keys, v1Box) }

        withMlKem {
            val v2Keys = generateV2Keys()
            assertFailsWith<ChaChaBoxError.SealingFailed> {
                ChaChaBox.seal(ChaChaBoxPublicKey.V2(zero, v2Keys.mlKemPublic), PLAINTEXT)
            }

            val encapsulated = mlKem768Encapsulate(v2Keys.mlKemPublic)
            val info =
                byteArrayOf(0x02) + encapsulated.ciphertext + zero + v2Keys.x25519Public + v2Keys.mlKemPublic
            val v2Key =
                hkdfBlake2b512(ByteArray(32) + encapsulated.sharedSecret, V2_SALT.encodeToByteArray(), info, 32)
            val v2Nonce = blake2bDigest(zero + v2Keys.x25519Public, 12)
            val v2Box =
                byteArrayOf(0x02) + encapsulated.ciphertext + zero +
                    chacha20Poly1305Encrypt(v2Key, v2Nonce, PLAINTEXT)
            assertFailsWith<ChaChaBoxError.UnsealingFailed> { ChaChaBox.unseal(v2Keys, v2Box) }
        }
    }

    @Test
    fun decodesPublicKeysByLength() {
        val v1 = generateV1Keys().publicKey
        assertEquals(v1, ChaChaBoxPublicKey.fromBytes(v1.toBytes()))

        withMlKem {
            val v2 = generateV2Keys().publicKey
            assertEquals(1216, v2.toBytes().size)
            assertEquals(v2, ChaChaBoxPublicKey.fromBytes(v2.toBytes()))
        }
    }

    @Test
    fun rejectsPublicKeysOfInvalidLength() {
        for (length in listOf(0, 31, 33, 1215, 1217)) {
            assertFailsWith<IllegalArgumentException>("length $length") {
                ChaChaBoxPublicKey.fromBytes(ByteArray(length))
            }
        }
    }

    @Test
    fun rejectsInvalidMlKemPublicKey() =
        withMlKem {
            val keys = generateV2Keys()
            // Lift the first 12-bit coefficient to 0xfff, beyond the modulus q = 3329.
            val invalid =
                keys.publicKey.toBytes().also {
                    it[32] = 0xff.toByte()
                    it[33] = (it[33].toInt() or 0x0f).toByte()
                }
            assertFailsWith<IllegalArgumentException> { ChaChaBoxPublicKey.fromBytes(invalid) }
            val unchecked = ChaChaBoxPublicKey.V2(keys.x25519Public, invalid.copyOfRange(32, invalid.size))
            assertFailsWith<ChaChaBoxError.SealingFailed> { ChaChaBox.seal(unchecked, PLAINTEXT) }
        }

    @Test
    fun rejectsSecretKeysOfInvalidLength() {
        val x25519 = x25519GenerateKeyPair()
        for (length in listOf(0, 63, 65)) {
            assertFailsWith<IllegalArgumentException>("length $length") {
                ChaChaBoxKeys.V2(x25519.secret, x25519.public, ByteArray(length))
            }
        }
        assertFailsWith<IllegalArgumentException> { ChaChaBoxKeys.V1(ByteArray(31), x25519.public) }
    }

    private fun assertUnsupportedVersion(
        version: Byte,
        block: () -> Unit,
    ) = assertEquals(version, assertFailsWith<ChaChaBoxError.UnsupportedVersion>(block = block).version)

    private fun ByteArray.withBitFlipped(position: Int): ByteArray =
        copyOf().also { it[position] = (it[position].toInt() xor 1).toByte() }

    private fun generateV1Keys(): ChaChaBoxKeys.V1 {
        val x25519 = x25519GenerateKeyPair()
        return ChaChaBoxKeys.V1(x25519.secret, x25519.public)
    }

    private fun generateV2Keys(): ChaChaBoxKeys.V2 = assertIs<ChaChaBoxKeys.V2>(ChaChaBox.generateKeys())

    private fun v1Keys(secret: String): ChaChaBoxKeys.V1 {
        val x25519Secret = Base64.decode(secret)
        return ChaChaBoxKeys.V1(x25519Secret, x25519PublicKey(x25519Secret))
    }

    /** Builds keys from the encoding `x25519_sk(32) || ml_kem_seed(64)`. */
    private fun v2Keys(secret: String): ChaChaBoxKeys.V2 {
        val bytes = Base64.decode(secret)
        val x25519Secret = bytes.copyOfRange(0, 32)
        return ChaChaBoxKeys.V2(x25519Secret, x25519PublicKey(x25519Secret), bytes.copyOfRange(32, bytes.size))
    }

    // X25519 with the base point yields the public key.
    private fun x25519PublicKey(secret: ByteArray): ByteArray = x25519SharedSecret(secret, X25519_BASE_POINT)

    private companion object {
        val PLAINTEXT: ByteArray = "wurzelpfropf".encodeToByteArray()
        val X25519_BASE_POINT: ByteArray = ByteArray(32).also { it[0] = 9 }
        const val V2_SALT: String = "chacha_box_ietf/v2"
    }
}

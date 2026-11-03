// Mirrors `CryptoPrimitives.kt`, the Kotlin expect surface for the
// settlement crypto.

import Crypto
import Foundation
@_spi(Interop) import RoutexCrypto

struct InteropError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

private let mlKemUnavailable = InteropError(message: "ML-KEM-768 requires iOS 26, macOS 26, or later")

@objc(RTXCryptoPrimitives)
public final class CryptoPrimitives: NSObject {
    @objc(sha256:)
    public static func sha256(_ data: Data) -> Data {
        Data(SHA256.hash(data: data))
    }

    @objc(blake2b:outputLength:)
    public static func blake2b(_ message: Data, outputLength: Int) -> Data {
        Blake2b.hash(message, outputLength: outputLength)
    }

    @objc(hkdfBlake2b512WithIkm:salt:info:length:)
    public static func hkdfBlake2b512(ikm: Data, salt: Data, info: Data, length: Int) -> Data {
        HKDFBlake2b512.deriveKey(ikm: ikm, salt: salt, info: info, length: length)
    }

    /// Returns `ciphertext || 16-byte tag`.
    @objc(chacha20Poly1305SealWithKey:nonce:plaintext:error:)
    public static func chacha20Poly1305Seal(
        key: Data, nonce: Data, plaintext: Data
    ) throws -> Data {
        let sealed = try ChaChaPoly.seal(
            plaintext, using: SymmetricKey(data: key), nonce: ChaChaPoly.Nonce(data: nonce))
        return sealed.ciphertext + sealed.tag
    }

    /// Expects `ciphertext || 16-byte tag`.
    @objc(chacha20Poly1305OpenWithKey:nonce:ciphertextWithTag:error:)
    public static func chacha20Poly1305Open(
        key: Data, nonce: Data, ciphertextWithTag: Data
    ) throws -> Data {
        guard ciphertextWithTag.count >= 16 else {
            throw InteropError(message: "ciphertext shorter than the Poly1305 tag")
        }
        let tagStart = ciphertextWithTag.endIndex - 16
        let box = try ChaChaPoly.SealedBox(
            nonce: ChaChaPoly.Nonce(data: nonce),
            ciphertext: ciphertextWithTag[ciphertextWithTag.startIndex..<tagStart],
            tag: ciphertextWithTag[tagStart...])
        return try ChaChaPoly.open(box, using: SymmetricKey(data: key))
    }

    @objc(x25519SharedSecretWithSecret:publicKey:error:)
    public static func x25519SharedSecret(secret: Data, publicKey: Data) throws -> Data {
        let priv = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: secret)
        let pub = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: publicKey)
        let shared = try priv.sharedSecretFromKeyAgreement(with: pub)
        return shared.withUnsafeBytes { Data($0) }
    }

    @objc(x25519GenerateSecretKey)
    public static func x25519GenerateSecretKey() -> Data {
        Curve25519.KeyAgreement.PrivateKey().rawRepresentation
    }

    @objc(x25519PublicKeyForSecret:error:)
    public static func x25519PublicKey(forSecret secret: Data) throws -> Data {
        try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: secret)
            .publicKey.rawRepresentation
    }

    @objc(randomBytes:)
    public static func randomBytes(_ length: Int) -> Data {
        SymmetricKey(size: SymmetricKeySize(bitCount: length * 8)).withUnsafeBytes { Data($0) }
    }

    @objc(mlKem768IsAvailable)
    public static func mlKem768IsAvailable() -> Bool {
        if #available(iOS 26, macOS 26, tvOS 26, watchOS 26, macCatalyst 26, visionOS 26, *) {
            return true
        }
        return false
    }

    @objc(mlKem768KeyPairForSeed:error:)
    public static func mlKem768KeyPair(forSeed seed: Data) throws -> MLKEM768KeyPair {
        guard #available(iOS 26, macOS 26, tvOS 26, watchOS 26, macCatalyst 26, visionOS 26, *) else {
            throw mlKemUnavailable
        }
        let key = try MLKEM768.PrivateKey(seedRepresentation: seed, publicKey: nil)
        return MLKEM768KeyPair(encapsulationKey: key.publicKey.rawRepresentation) { ciphertext in
            try key.decapsulate(ciphertext).withUnsafeBytes { Data($0) }
        }
    }

    // CryptoKit defers the FIPS 203 encapsulation key check (section 7.2) to encapsulation.
    @objc(mlKem768IsValidEncapsulationKey:)
    public static func mlKem768IsValidEncapsulationKey(_ encapsulationKey: Data) -> Bool {
        (try? mlKem768Encapsulate(encapsulationKey: encapsulationKey)) != nil
    }

    /// Returns `ciphertext || 32-byte shared secret`.
    @objc(mlKem768EncapsulateWithEncapsulationKey:error:)
    public static func mlKem768Encapsulate(encapsulationKey: Data) throws -> Data {
        guard #available(iOS 26, macOS 26, tvOS 26, watchOS 26, macCatalyst 26, visionOS 26, *) else {
            throw mlKemUnavailable
        }
        let result = try MLKEM768.PublicKey(rawRepresentation: encapsulationKey).encapsulate()
        let sharedSecret = result.sharedSecret.withUnsafeBytes { Data($0) }
        return result.encapsulated + sharedSecret
    }

    @objc(ed25519VerifyWithPublicKey:message:signature:)
    public static func ed25519Verify(
        publicKey: Data, message: Data, signature: Data
    ) -> Bool {
        guard let key = try? Curve25519.Signing.PublicKey(rawRepresentation: publicKey) else {
            return false
        }
        return key.isValidSignature(signature, for: message)
    }
}

@objc(RTXMLKEM768KeyPair)
public final class MLKEM768KeyPair: NSObject {
    @objc public let encapsulationKey: Data
    private let decapsulator: (Data) throws -> Data

    init(encapsulationKey: Data, decapsulator: @escaping (Data) throws -> Data) {
        self.encapsulationKey = encapsulationKey
        self.decapsulator = decapsulator
    }

    @objc(decapsulate:error:)
    public func decapsulate(_ ciphertext: Data) throws -> Data {
        try decapsulator(ciphertext)
    }
}

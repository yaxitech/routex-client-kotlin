// Mirrors `VcekCertificate.kt`, the Kotlin expect surface for X.509 chain
// validation. RoutexCrypto's parser is used because the Apple X.509 stack
// cannot parse AMD's RSASSA-PSS-signed certificates.

import Crypto
import Foundation
@_spi(Interop) import RoutexCrypto
import SwiftASN1

@objc(RTXCertificate)
public final class Certificate: NSObject {
    let certificate: AmdCertificate

    /// DER-encoded subject distinguished name.
    @objc public var subjectDN: Data { certificate.subjectDN }

    /// DER-encoded issuer distinguished name.
    @objc public var issuerDN: Data { certificate.issuerDN }

    /// The subject's `commonName` (OID 2.5.4.3), or `nil` when absent.
    @objc public var subjectCommonName: String? { certificate.subjectCommonName }

    /// Start of the validity window (`notBefore`).
    @objc public var notValidBefore: Date { certificate.notValidBefore }

    /// End of the validity window (`notAfter`).
    @objc public var notValidAfter: Date { certificate.notValidAfter }

    /// Whether Basic Constraints marks this certificate as a CA (`cA = true`).
    @objc public var isCertificateAuthority: Bool { certificate.isCertificateAuthority }

    /// Basic Constraints `pathLenConstraint`; -1 when unconstrained or absent
    /// (Objective-C cannot carry an optional Int).
    @objc public var maxIntermediateCertificates: Int {
        certificate.maxIntermediateCertificates ?? -1
    }

    /// Whether Key Usage permits certificate signing (`keyCertSign`).
    @objc public var allowsCertificateSigning: Bool { certificate.allowsCertificateSigning }

    init(_ certificate: AmdCertificate) {
        self.certificate = certificate
    }

    /// Parse a PEM bundle into certificates, in document order.
    @objc(parseChainWithPem:error:)
    public static func parseChain(pem: String) throws -> [Certificate] {
        try AmdCertificate.parseChain(pem: pem).map(Certificate.init)
    }

    /// Verify this certificate's signature against `issuer`'s public key.
    @objc(verifySignatureBy:error:)
    public func verifySignature(by issuer: Certificate) throws {
        try certificate.verifySignature(issuedBy: issuer.certificate)
    }

    @objc(hasExtension:)
    public func hasExtension(_ oid: String) -> Bool {
        guard let parsed = try? Self.parseOID(oid) else { return false }
        return certificate.extensions[parsed] != nil
    }

    /// Decode the extension at `oid` as an IA5String. Call only after
    /// `hasExtension` returned true; throws when the value is malformed.
    @objc(ia5StringExtensionWithOid:error:)
    public func ia5StringExtension(oid: String) throws -> String {
        guard let value = try certificate.ia5StringExtension(Self.parseOID(oid)) else {
            throw InteropError(message: "extension \(oid) is not present")
        }
        return value
    }

    /// Decode the extension at `oid` as an INTEGER. Call only after
    /// `hasExtension` returned true; throws when the value is malformed.
    @objc(integerExtensionWithOid:error:)
    public func integerExtension(oid: String) throws -> NSNumber {
        guard let value = try certificate.integerExtension(Self.parseOID(oid)) else {
            throw InteropError(message: "extension \(oid) is not present")
        }
        return NSNumber(value: value)
    }

    /// Verify an ECDSA P-384 + SHA-384 signature over `message` using this
    /// certificate's own public key. `rawSignature` is the 96-byte
    /// big-endian `r || s` scalar pair.
    @objc(verifyEcdsaP384Sha384WithMessage:rawSignature:)
    public func verifyEcdsaP384Sha384(message: Data, rawSignature: Data) -> Bool {
        guard let key = try? certificate.p384PublicKey(),
            let signature = try? P384.Signing.ECDSASignature(rawRepresentation: rawSignature)
        else {
            return false
        }
        return key.isValidSignature(signature, for: SHA384.hash(data: message))
    }

    private static func parseOID(_ oid: String) throws -> ASN1ObjectIdentifier {
        do {
            return try ASN1ObjectIdentifier(dotRepresentation: Substring(oid))
        } catch {
            throw InteropError(message: "invalid OID \(oid): \(error)")
        }
    }
}

// Hand-maintained interface for the RoutexKotlinInterop facade, consumed by
// Kotlin/Native cinterop; every selector is pinned in Swift via `@objc(...)`.

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface RTXMLKEM768KeyPair : NSObject

- (instancetype)init NS_UNAVAILABLE;

@property(nonatomic, readonly) NSData *encapsulationKey;

- (nullable NSData *)decapsulate:(NSData *)ciphertext error:(NSError **)error;

@end

@interface RTXCryptoPrimitives : NSObject

- (instancetype)init NS_UNAVAILABLE;

+ (NSData *)sha256:(NSData *)data;
+ (NSData *)blake2b:(NSData *)message outputLength:(NSInteger)outputLength;
+ (NSData *)hkdfBlake2b512WithIkm:(NSData *)ikm
                             salt:(NSData *)salt
                             info:(NSData *)info
                           length:(NSInteger)length;
+ (nullable NSData *)chacha20Poly1305SealWithKey:(NSData *)key
                                           nonce:(NSData *)nonce
                                       plaintext:(NSData *)plaintext
                                           error:(NSError **)error;
+ (nullable NSData *)chacha20Poly1305OpenWithKey:(NSData *)key
                                           nonce:(NSData *)nonce
                               ciphertextWithTag:(NSData *)ciphertextWithTag
                                           error:(NSError **)error;
+ (nullable NSData *)x25519SharedSecretWithSecret:(NSData *)secret
                                        publicKey:(NSData *)publicKey
                                            error:(NSError **)error;
+ (NSData *)x25519GenerateSecretKey;
+ (nullable NSData *)x25519PublicKeyForSecret:(NSData *)secret error:(NSError **)error;
+ (NSData *)randomBytes:(NSInteger)length;
+ (BOOL)mlKem768IsAvailable;
+ (nullable RTXMLKEM768KeyPair *)mlKem768KeyPairForSeed:(NSData *)seed error:(NSError **)error;
+ (BOOL)mlKem768IsValidEncapsulationKey:(NSData *)encapsulationKey;
+ (nullable NSData *)mlKem768EncapsulateWithEncapsulationKey:(NSData *)encapsulationKey
                                                       error:(NSError **)error;
+ (BOOL)ed25519VerifyWithPublicKey:(NSData *)publicKey
                           message:(NSData *)message
                         signature:(NSData *)signature;

@end

@interface RTXCertificate : NSObject

- (instancetype)init NS_UNAVAILABLE;

@property(nonatomic, readonly) NSData *subjectDN;
@property(nonatomic, readonly) NSData *issuerDN;
@property(nonatomic, readonly, nullable) NSString *subjectCommonName;
@property(nonatomic, readonly) NSDate *notValidBefore;
@property(nonatomic, readonly) NSDate *notValidAfter;
@property(nonatomic, readonly) BOOL isCertificateAuthority;
@property(nonatomic, readonly) NSInteger maxIntermediateCertificates;
@property(nonatomic, readonly) BOOL allowsCertificateSigning;

+ (nullable NSArray<RTXCertificate *> *)parseChainWithPem:(NSString *)pem error:(NSError **)error;
- (BOOL)verifySignatureBy:(RTXCertificate *)issuer error:(NSError **)error;
- (BOOL)hasExtension:(NSString *)oid;
- (nullable NSString *)ia5StringExtensionWithOid:(NSString *)oid error:(NSError **)error;
- (nullable NSNumber *)integerExtensionWithOid:(NSString *)oid error:(NSError **)error;
- (BOOL)verifyEcdsaP384Sha384WithMessage:(NSData *)message rawSignature:(NSData *)rawSignature;

@end

NS_ASSUME_NONNULL_END

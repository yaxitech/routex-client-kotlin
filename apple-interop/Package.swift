// swift-tools-version: 6.1

// Objective-C facade over routex-client-swift, sized to the expect/actual
// seam of the Kotlin Apple targets; built by Gradle and embedded into the
// klibs via cinterop, never published. Selectors are pinned with
// `@objc(...)` to match `include/RoutexKotlinInterop.h`.
import PackageDescription

let package = Package(
    name: "routex-kotlin-interop",
    platforms: [
        .iOS(.v15),
        .macOS(.v12),
    ],
    products: [
        .library(name: "RoutexKotlinInterop", type: .static, targets: ["RoutexKotlinInterop"])
    ],
    dependencies: [
        .package(url: "https://github.com/yaxitech/routex-client-swift", from: "0.5.0"),
        .package(url: "https://github.com/apple/swift-crypto", from: "4.5.0"),
        .package(url: "https://github.com/apple/swift-asn1", from: "1.7.0"),
    ],
    targets: [
        .target(
            name: "RoutexKotlinInterop",
            dependencies: [
                .product(name: "RoutexCrypto", package: "routex-client-swift"),
                .product(name: "Crypto", package: "swift-crypto"),
                .product(name: "SwiftASN1", package: "swift-asn1"),
            ]
        )
    ],
    swiftLanguageModes: [.v6]
)

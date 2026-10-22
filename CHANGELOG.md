# Changelog

## [0.9.0] - 2026-10-22

### Added

- `standingOrders`, `respondStandingOrders` and `confirmStandingOrders`
- `Account.maskedPan` and `AccountField.MaskedPan`, the masked payment card number, and `AccountReference.maskedPan(maskedPan, currency?)` to reference a card account in the balances and transactions services

### Changed

- **Breaking:** `AccountIdentifier` and `AccountField` gain a `MaskedPan` subclass, so exhaustive `when` expressions over them need an additional branch.
- Updated the dependencies that reach consumers: Kotlin 2.4.20, Ktor 3.6.0, and, at runtime on JVM and Android, BouncyCastle 1.86.

### Fixed

- `Amount(String, String)` throws `IllegalArgumentException` for all malformed input and parses scientific notation correctly.

## [0.8.0] - 2026-09-01

### Added

- Apple targets (`macosArm64`, `iosArm64`, `iosSimulatorArm64`; macOS 12 / iOS 15 baselines) for all published modules except the backend-only `routex-refresh-client`.
  The settlement crypto and X.509 primitives are backed by [routex-client-swift](https://github.com/yaxitech/routex-client-swift) through the `apple-interop` facade.
- `accounts`, `balances` and `transactions` overloads taking a `ConnectionData`, so a service can be run with the `connectionData` of an earlier `Response.Result` instead of `Credentials`.
- `InterruptError`, indicating that a request without credentials cannot complete without user interaction.

### Changed

- `@Throws` on the public suspend API now also lists `CancellationException`, as Kotlin/Native requires; JVM consumers are unaffected.

## [0.7.0] - 2026-08-02

### Added

- `ConnectionInfo.labels`

### Security

- Reject attestation reports whose guest policy allows migration to another host.

## [0.6.1] - 2026-07-28

### Changed

- Updated the dependencies that reach consumers: Kotlin 2.4.10, Ktor 3.5.1, kotlinx-coroutines 1.11.0, kotlinx-datetime 0.8.0, and, at runtime on JVM and Android, BouncyCastle 1.85; kotlinx-datetime is pre-1.0 and breaks between minors, so a consumer pinned to 0.7.1 resolves up to 0.8.0.

## [0.6.0] - 2026-07-16

### Added

- Kotlin/JS target.
- Reference accounts that have no IBAN in the balances and transactions services via `AccountReference.number(number, currency?)` (e.g. an ISO 20022 BBAN). `AccountReference` now carries a sealed `AccountIdentifier` (`Iban` | `Number`) in `id`.
- `IbanReference`, a plain-IBAN debtor account reference for the collect-payment and transfer services.
- `CollectPaymentField.EncryptedDebtorName` to request the debtor name in encrypted form (`PaymentInitiation.encryptedDebtorName`), mirroring `EncryptedDebtorIban`.
- `Balance.dateTime`, the moment a balance is valid (ISO 8601 date-time).
- `userInSession` constructor option on `RoutexRefreshClient` / `RoutexRefreshAsyncClient` (`UserInSession.OnThisConnection` or `UserInSession.At(ipAddress)`) to signal an in-session user.

### Changed

- **Breaking:** construct an `AccountReference` with the `AccountReference.iban(iban, currency?)` / `AccountReference.number(...)` factories instead of `AccountReference(iban, currency?)`; the identifier is read from `id` rather than a top-level `iban` field.
- **Breaking:** the IBAN-only payment-creditor identifier is renamed `AccountIdentifier` → `CreditorAccountIdentifier` (the `creditorAccount` type for the collect-payment and transfer services); `AccountIdentifier` now denotes the account-information reference identifier.
- **Breaking:** `AccountReference` is no longer a `DebtorAccountReference`; pass the collect-payment/transfer debtor account as an `IbanReference` (plain IBAN) instead of an `AccountReference`. Encrypted debtors continue to use `EncryptedIbanReference`.

## [0.5.0] - 2026-06-14

### Added

- `ConnectionInfo.bankCodes`, the national bank codes. Request it by passing `ConnectionDetails.BANK_CODES` to `search`.

### Changed

- Enforce X.509 validity, Basic Constraints, Key Usage, and subject CN constraints on VCEK chains.

### Removed

- `RoutexError.ConsentExpired`. Expired consents now surfaces as `Unauthorized`.

### Security

- Reject VCEK chains anchored at a self-signed certificate that is not a pinned AMD root.

## [0.4.0] - 2026-05-19

### Added

- `:tickets` module with `RoutexTicketIssuer`, which issues signed and typed JWT tickets that authorize service calls.

## [0.3.0] - 2026-05-12

Native Kotlin Multiplatform rewrite.

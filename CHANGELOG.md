# Changelog

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

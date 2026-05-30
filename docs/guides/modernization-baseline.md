# MobileMail 2026 Modernization Baseline

This file freezes baseline checks before large modernization waves.

## Critical user flows

- Login with existing OAuth session.
- Fresh OAuth login.
- Inbox load and pagination.
- Message detail opening (plain + HTML).
- Sending messages and processing offline queue.
- Push delivery and target navigation.
- Logout and account switch.

## Smoke checks after each wave

1. `./gradlew :app:assembleDebug`
2. `./gradlew :app:lintDebug`
3. `./gradlew :app:testDebugUnitTest`
4. Manual run of critical user flows.

For release-hardening waves:

1. `./gradlew :app:assembleRelease`
2. Manual run on release artifact for critical flows.

## Rollback

1. Keep each migration wave in a separate commit.
2. Revert only the latest wave if regression appears.
3. Re-run debug smoke and the affected manual flows.

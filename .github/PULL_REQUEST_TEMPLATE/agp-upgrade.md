---
name: AGP Upgrade
about: Pull request template for Android Gradle Plugin / Gradle upgrade
---

## Summary

Describe the target AGP/Gradle/Kotlin versions and motivation.

- Target AGP: <!-- e.g. 9.3.0-alpha09 -->
- Gradle wrapper: <!-- e.g. gradle-9.4.1 -->
- Kotlin: <!-- e.g. 2.2.20 -->

## Checklist

- [ ] CI: `./gradlew clean assembleDebug` passes
- [ ] Unit tests pass: `./gradlew test`
- [ ] Migration tests added and passing (Room migrations)
- [ ] Manual QA: login flows, chat sync, attachments tested on device/emulator
- [ ] Documented rollback steps and backup instructions for local DB
- [ ] Update `CHANGELOG.md` with compatibility notes

## Migration / Compatibility notes

Add description of migration changes and why they are safe. Include links to `docs/AGP_UPGRADE_PLAN.md` and any relevant migration tests.

## How to test locally

```bash
./gradlew --no-daemon clean assembleDebug
./gradlew --no-daemon test
./gradlew --no-daemon :app:connectedAndroidTest  # optional, requires device/emulator
```

## Rollback Plan

Describe how to revert the change if CI or QA fails (e.g., revert PR, push previous artifact, restore DB backup).

# Changelog

All notable changes to this project will be documented in this file.

## [0.1.0] - 2026-06-01
### Added
- `CHANGELOG.md` — initial changelog entry.

### Fixed
- Attachments upload failing with Storage 404: added detailed logging of `bucket` and `path` in `ChatRepository`.
- Prevent uploads when no active session or no network in `ChatViewModel`.

### Changed
- Added retry UI for failed uploads (Snackbar with "Повторить") in `ChatFragment`.
- Compressed image bytes before upload and improved error handling in upload flow.

### Docs
- `docs/FIREBASE_CHECKS.md` — instructions to enable Firestore/Storage and verify Auth.
- `PR_DESCRIPTION.md` — reproduction steps and checklist.




PR: Fix attachments upload, error handling, and retry UI

Краткое описание изменений
- Добавлена логика логирования bucket и path при загрузках (ChatRepository).
- Добавлена пред-проверка сессии и сети перед загрузкой (ChatViewModel).
- Добавлен SharedFlow для ошибок загрузки и UI-ретрай (ChatFragment).
- Улучшена обработка ошибок при upload (подробные логи и сообщения для UI).
- Утилиты: скрипт `scripts/extract_logs.ps1` для извлечения релевантных логов.

Как воспроизвести (локально, на тестовом устройстве)
1. Установить debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Запустить приложение, авторизоваться (если требуются учетные данные).
3. Открыть заявку (например, request id=2), прикрепить изображение и отправить.
4. Наблюдать поведение — если загрузка не проходит, собрать логи:

adb logcat -v time ChatRepository:V ChatViewModel:V Firebase:V Firestore:V Storage:V *:S > log_upload.txt

Критические проверки перед мерджем (обязательно)
- Включить Cloud Firestore API для проекта `gingerfoxaatelie` в Google Cloud Console.
  https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=gingerfoxaatelie
- Проверить, что Storage bucket `gingerfoxaatelie.firebasestorage.app` существует и совпадает с `app/google-services.json`.
- Убедиться, что правила Storage позволяют запись для тестовой среды (или что пользователь авторизован и правила это позволяют).
- Убедиться, что Firebase Auth корректно инициализирован и тестовый пользователь залогинен.
- Подождать несколько минут после включения API для распространения изменений.

Файлы для ревью
- app/src/main/java/com/ginger/android/data/repository/ChatRepository.kt
- app/src/main/java/com/ginger/android/ui/requests/ChatViewModel.kt
- app/src/main/java/com/ginger/android/ui/requests/ChatFragment.kt
- scripts/extract_logs.ps1
- docs/FIREBASE_CHECKS.md

Changelog
-------
- v0.1.0 — 2026-06-01
  - Fix: attachments upload failing with Storage 404 — added detailed logging of `bucket` and `path` in `ChatRepository`.
  - Fix: prevent upload when no active session or no network in `ChatViewModel`.
  - Feature: retry UI for failed uploads (Snackbar with "Повторить") in `ChatFragment`.
  - Improvement: compress image bytes before upload and use `putBytes` with better error handling.
  - Docs: `docs/FIREBASE_CHECKS.md` and `PR_DESCRIPTION.md` updated with reproduction steps and Firebase checks.
  - Tooling: `scripts/extract_logs.ps1` for extracting relevant log entries (UTF-16 aware).

Commit message suggestion
------------------------
`Fix attachments upload: add logging, pre-checks, retry UI, docs`

Шаблон описания PR (скопировать в поле PR)
Title: Fix attachments upload + add retry UI + add logs
Body:
- Что изменено: логирование bucket/path; пред-проверки авторизации/сети; обработка ошибок; UI-ретрай.
- Почему: загрузки падали с Storage 404 и Firestore PERMISSION_DENIED на тестовом устройстве.
- Как проверить: шаги воспроизведения выше; приложить `log_upload.txt` при ошибках.
- Требования перед мерджем: включенный Firestore API, проверенный bucket, рабочая авторизация.

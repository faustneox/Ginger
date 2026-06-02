Добавляет скелет для безопасного апгрейда Android Gradle Plugin (AGP).

Что включено в этот PR (skeleton, без изменения версий):
- Документ с планом апгрейда: `docs/AGP_UPGRADE_PLAN.md`
- Шаблон PR для апгрейда: `.github/PULL_REQUEST_TEMPLATE/agp-upgrade.md`
- Скелет теста миграции Room `16->17`: `app/src/androidTest/java/com/ginger/android/migration/Migration16To17Test.kt`
- CI workflow для проверки сборки, unit-тестов и запуска instrumentation test на эмуляторе: `.github/workflows/agp-upgrade-ci.yml`

Цель: подготовить инфраструктуру для безопасного обновления AGP/Gradle и плагинов (Kotlin/KSP/Hilt), прогнать миграционные тесты и CI перед фактическим bump'ом версий.

Инструкции для локальной проверки:
```bash
./gradlew --no-daemon clean assembleDebug --refresh-dependencies
./gradlew --no-daemon test
./gradlew --no-daemon :app:connectedAndroidTest  # требует эмулятор/device
```

Это только подготовительный PR. После прохождения CI и ручного QA я предложу конкретный bump AGP/Kotlin и внесу корректировки в зависимости.

Пожалуйста, назначьте ревьюеров и укажите желаемую целевую ветку для финального bump'а (обычно `main` или `develop`).

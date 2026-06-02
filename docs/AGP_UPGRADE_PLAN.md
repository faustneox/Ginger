# План безопасного апгрейда Gradle / Android Gradle Plugin (AGP)

Цель: подготовить воспроизводимый, тестируемый и откатываемый процесс обновления AGP/Gradle и сопутствующих плагинов (Kotlin, KSP, Hilt), минимизируя риск для пользователей и данных (особенно миграций Room).

Текущие версии (на момент плана):
- Gradle wrapper: `gradle-9.4.1` (см. `gradle/wrapper/gradle-wrapper.properties`)
- Android Gradle Plugin (AGP): `9.2.1` (каталог `gradle/libs.versions.toml`)
- Kotlin plugin: `2.2.20` (каталог `gradle/libs.versions.toml`)

Рекомендация: держать текущую конфигурацию стабильной (`AGP 9.2.1`) и подготовить PR с планом и тестами для безопасного перехода на целевую версию (например, `9.3.0-alpha09` — experimental) только после прохождения CI и прогонки миграционных тестов.

Шаги для безопасного апгрейда
1) Подготовка ветки
   - Создать ветку: `chore/agp-upgrade/<target-version>` (например `chore/agp-upgrade/9.3.0-alpha09` или `chore/agp-upgrade/9.2.x` для мелких фиксов).

2) Базовые проверки совместимости
   - Проверить доступность AGP-артефакта в Google Maven (`https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/`).
   - Проверить требования AGP к Gradle wrapper и JDK (см. релиз-ноты AGP).
   - Проверить совместимость Kotlin/KSP/Hilt с целевой AGP-версией.

3) Подготовка изменений без применения
   - В ветке подготовить изменения в `gradle/libs.versions.toml` (обновить `agp` и опционально `kotlin`, `ksp`), но не менять `gradle-wrapper.properties` пока не уверены.
   - Добавить файл `docs/AGP_UPGRADE_PLAN.md` (этот документ) и тесты миграций/сборки.

4) Миграционные тесты Room
   - Добавить unit/instrumentation тест для миграции `16->17` (и других релевантных), который воспроизводит БД со старыми данными (duplicate `firestore_id`) и прогоняет миграцию.
   - Шаблон: использовать `androidx.room:room-testing` и `MigrationTestHelper` для проверки, что уникальный индекс создаётся и данные корректно очищены/мигрированы.

5) CI и матричное тестирование
   - Добавить CI job/matrix, который собирает проект на целевых парах: (AGP baseline, AGP candidate) × (Kotlin baseline, Kotlin candidate).
   - CI должен запускать: `./gradlew --no-daemon clean assembleDebug test` и migration tests; по возможности — `connectedAndroidTest` в отдельном job.

6) Локальная проверка и исправления
   - Команды для локального прогона:
     ```bash
     ./gradlew --no-daemon clean assembleDebug --refresh-dependencies
     ./gradlew --no-daemon test
     ./gradlew --no-daemon :app:connectedAndroidTest  # optional, requires device/emulator
     ```
   - Фиксировать и коммитить проблемы поодиночке, документируя изменения в PR.

7) QA и развертывание
   - Перед релизом подготовить internal QA сборку и воспроизвести основные сценарии: вход (Google, телефон/пароль), отправка/синхрон чата, миграции старых БД.
   - Сделать бэкап/экспорт локальной БД пользователей (инструкция в PR) перед выкатыванием.

8) План отката
   - Если проблемы на этапе CI или QA: откатить ветку/PR; для прод-выкаток — откатить релизную сборку (rollback artifact) и уведомить команду.

9) PR checklist (обязательно)
   - CI: `assembleDebug` и `test` — зелёные.
   - Migration tests — зелёные.
   - Информация по версиям: `gradle-wrapper.properties`, `gradle/libs.versions.toml` (AGP/Kotlin/KSP), `gradle.properties` изменения.
   - Инструкция по локальному тестированию и бэкапу базы (short steps).
   - Подписанты: минимум 1 бекенд/DB-ответственный и 1 Android reviewer.

Дополнитель заметки и варианты действий
- Если плагин AGP не разрешается через plugins DSL (плагин-репозиторий), временно использовать classpath fallback в `build.gradle.kts` и `apply(plugin = ...)`, но это требует осторожности (конфликты с plugin DSL возможны). В репозитории это можно подготовить в экспериментальной ветке, но не мёрджить в main без CI-проверок.
- Для production-релиза рекомендуем сначала протестировать в staging/internal track и только затем выпускать в release.

Контакты/следующие шаги
- Если хотите — я могу автоматически подготовить ветку + PR skeleton: обновлю `gradle/libs.versions.toml` (с предлагаемой целевой версией), добавлю тест-скелет миграции и CI workflow skeleton. Подтвердите, если готов двигаться дальше.

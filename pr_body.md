Title: chore(ci): add unit tests workflow and align Kotlin stdlib

Body:
Добавляет GitHub Actions workflow для запуска unit‑тестов модуля `app` и загрузки артефактов (JUnit XML + HTML).

Изменения:
- `gradle/libs.versions.toml`: обновлён `kotlin` → `2.2.20`
- `app/build.gradle.kts`: добавлен явный `kotlin-stdlib` и `resolutionStrategy.force` для стабилизации stdlib
- `.github/workflows/android-test-results.yml`: добавлен workflow; job выполняется только при наличии маркера `[ci run]` в сообщении коммита или в заголовке/описании PR

Как запустить локально:
```powershell
cd "C:\Users\AntonM\Desktop\Ginger"
.\gradlew.bat clean :app:testDebugUnitTest --no-daemon --rerun-tasks --console=plain
```

Как триггерить CI:
- Добавьте `[ci run]` в сообщение коммита или в заголовок/описание PR, например:
```powershell
git commit -m "chore(ci): add workflow and align Kotlin stdlib [ci run]"
git push origin ci/add-test-report-workflow
```

Чеклист:
- [ ] Локальные тесты проходят
- [ ] CI Actions успешно выполнен и артефакты доступны
- [ ] KSP совместимость подтверждена

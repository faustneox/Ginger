Пошаговая инструкция: проверки Firebase перед тестированием и мерджем

1) Проверка Cloud Firestore API
- Перейдите в Google Cloud Console (профиль проекта `gingerfoxaatelie`):
  https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=gingerfoxaatelie
- Нажмите "Enable" если API выключен.
- Альтернативно: в Firebase Console откройте Firestore и инициализируйте базу данных (требуется один раз).

2) Проверка Storage bucket
- Откройте Firebase Console → Storage и убедитесь, что bucket указан как `gingerfoxaatelie.firebasestorage.app`.
- Сверьте значение с `app/google-services.json` → `project_info.storage_bucket`.
- Для отладки временно упростите правила (например, allow read, write: if true) и протестируйте загрузку.
  ВАЖНО: не оставляйте открытые правила в проде.

3) Проверка правил безопасности
- Если правила требуют аутентификацию, убедитесь, что в приложении пользователь залогинен (Firebase Auth).
- Вариант для тестирования: включите Email/Password sign-in и создайте тестового пользователя.

4) App Check и токены
- Если в проекте включён App Check — либо временно отключите его для тестовой сборки, либо используйте подходящий провайдер (debug token).
- При ошибках типа "FirebaseNoSignedInUserException" проверьте поток авторизации в приложении.

5) Сеть и DNS
- Убедитесь, что тестовое устройство имеет стабильное подключение и резолвит firestore.googleapis.com.
- Попробуйте сменить Wi‑Fi / отключить VPN на устройстве.

6) Сбор логов
- Команда для сбора релевантных логов на хосте (в каталоге репозитория):

adb logcat -v time ChatRepository:V ChatViewModel:V Firebase:V Firestore:V Storage:V *:S > log_upload.txt

- Для удобства в репозитории есть скрипт `scripts/extract_logs.ps1`, который фильтрует `log_upload.txt` и сохраняет `log_filtered.txt`.

7) Что приложить к PR
- `log_upload.txt` и `log_filtered.txt` с момента воспроизведения ошибки.
- Скриншоты/видео, если поведение UI неочевидно.

8) Время ожидания
- После включения API в консоли может потребоваться несколько минут для распространения — подождите и повторите попытку.

# Отдельное тестовое окружение LR_3

Из каталога LR_3 в PowerShell:

```powershell
.\test.cmd
```

Понадобятся Docker Desktop с Linux containers, JDK 17+, Python 3.10+,
Node.js/npm и установленный Google Chrome. JDK обнаруживается так же, как
при обычном запуске; при необходимости укажи JAVA_HOME в testing/.env.
При первом запуске нужны сеть для образа postgres:17, Maven и npm-зависимостей.

## Что запускается

| | Рабочее приложение | Тестовое окружение |
|---|---|---|
| Команда | start.cmd | test.cmd |
| Docker Compose проект | itmo-lr3 | itmo-lr3-tests |
| PostgreSQL | порт 55432, база students | порт 55433, база students_test |
| Java API | порт 8081 в текущей настройке | порт 18083 |
| Frontend | порт 5500 | порт 15500 |
| Хранилище БД | постоянный том | временное tmpfs, пустое при новом контейнере |
| Конфигурация | server/.env | testing/.env.example + необязательный testing/.env |

Порты рабочего проекта могут отличаться: они заданы в его локальном .env.
Два окружения могут работать одновременно, если их порты не пересекаются.

Используется настоящий код server/src/main и тот же frontend. Отдельной копии
бизнес-логики нет. Java-тесты находятся в server/src/test, браузерные — tests/browser.

Последовательность test.cmd:

1. Проверить инструменты и свободные порты. Чужие процессы не останавливаются.
2. Запустить PostgreSQL из testing/compose.yaml и дождаться готовности.
3. Выполнить Maven verify: модульные и интеграционные тесты с профилем integration-test.
   Flyway создаёт настоящую схему из миграций в пустой тестовой БД.
4. Установить npm-зависимости по lockfile при первом запуске/изменении lockfile.
5. Запустить собранный Java JAR с тестовой БД и тестовым ADMIN e2e-admin.
   Пароль e2e-admin-password предназначен только для временного окружения.
6. Запустить Playwright в Chrome: интерфейс, HTTP-запросы к настоящему серверу,
   CRUD, роли, refresh и Swagger. Статический сервер предоставляет только публичные файлы.
7. Остановить временный Java-сервер и удалить только тестовый Compose-проект.
   Перед очисткой также выполняется Postman/Newman на том же тестовом сервере.
   Тестовые данные не сохраняются. Python-проверки скриптов входят в запуск.

Обновляемая рабочая база и исходный students.json не используются. Пароль
PostgreSQL и JWT-ключ генерируются заново для каждого запуска, не печатаются
и не читаются из server/.env. TEST_DB_URL и DB_URL собираются скриптом:
перенаправлять их на произвольную базу через testing/.env нельзя.

## Другие режимы

```powershell
# Только проверить настройки и наличие инструментов, без Docker и тестов:
.\test.cmd --check

# Java-тесты с отдельным PostgreSQL, без браузера и npm:
.\test.cmd --java-only
```

Чтобы изменить порты, скопируй testing/.env.example в testing/.env и задай
TEST_DB_PORT, TEST_API_PORT, TEST_FRONTEND_PORT. Скрипт принимает только эти
параметры и JAVA_HOME. testing/.env исключён из Git; example включён.

## Результаты и демонстрация

Логи лежат в .runtime/testing/: java.log, browser.log, backend.log,
database.log, cleanup.log. Итог — result.json. При ошибке команда возвращает
ненулевой код и указывает лог. Скриншоты/отчёты Playwright — в test-results/.
Эти результаты исключены из Git. При штатном завершении и Ctrl+C скрипт
пытается освободить временные ресурсы; повторный запуск одновременно запрещён.

Для демонстрации запусти test.cmd и открой Docker Desktop: во время выполнения
виден отдельный проект itmo-lr3-tests. В java.log видно подключение Flyway к
students_test. После завершения покажи PASS и result.json; тестовый проект удаляется.

Если окно принудительно закрыли или компьютер выключился, очисти только тестовый
проект следующей командой из LR_3 (tmpfs-данные при остановке теряются):

```powershell
$env:TEST_DB_PASSWORD = 'cleanup-only'
docker compose --project-name itmo-lr3-tests --env-file testing/.env.example -f testing/compose.yaml down --volumes --remove-orphans
Remove-Item Env:TEST_DB_PASSWORD
```

Не заменяй имя проекта или файл Compose рабочими. Рабочий start.cmd/stop.cmd
не управляет тестовым проектом. При занятом порте проверь источник конфликта;
скрипт не подключается к уже запущенному серверу вместо своего.

Итог конкретного запуска смотри в .runtime/testing/result.json. Логи Python
и Newman сохраняются рядом в python.log и postman.log.

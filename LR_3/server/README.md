# Сервер ЛР 3: Spring Boot + EclipseLink/JPA + PostgreSQL

Рабочая настройка выполнена. Рекомендуемый запуск на этом компьютере —
`../start.cmd`, остановка — `../stop.cmd`; подробности в [../RUN.md](../RUN.md).
Ниже также сохранены команды для самостоятельного запуска компонентов.

Выполнены шаги 1–10: Java-проект, контракт API, миграции БД, сущности/репозитории,
DTO/маппер, контроллер и транзакционный сервис студентов, валидация и единые ошибки.
CRUD, PUT/PATCH, QUERY, поиск, фильтры, сортировка и пагинация реализованы.
Регистрация, вход, JWT access/refresh и роли реализованы. Статический интерфейс
в родительской папке подключён к Java API. Python-сервер сохранён отдельно.

- [Контракт API](docs/api-contract.md)
- [Архитектура и схема БД](docs/architecture.md)
- [Авторизация и роли](docs/authentication.md)
- [Результаты проверок](docs/verification.md)
- [Swagger/OpenAPI и Postman](docs/api-checks.md)

## Окружение

Нужен JDK 17 или совместимый более новый JDK. Одного JRE или Java 8 недостаточно.
Maven отдельно устанавливать не требуется: `mvnw.cmd` (Windows) / `./mvnw` (Linux/macOS)
скачивает Maven 3.9.9 при первом запуске. Для первого запуска нужен интернет.

В PowerShell выбери установленный JDK (пример пути замени на свой):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
./mvnw.cmd --version
```

Команды ниже выполняются из `LR_3/server`.

## PostgreSQL через Docker Compose

1. Запусти Docker Desktop с Linux containers.
2. Скопируй `.env.example` в `.env` и установи свой DB_PASSWORD.
3. Запусти БД:

```powershell
Copy-Item .env.example .env
# Здесь отредактируй DB_PASSWORD в .env, затем:
docker compose up -d postgres
docker compose ps
```

Данные PostgreSQL сохраняются в именованном Docker volume, порт публикуется
только на localhost. Если 5432 занят, измени DB_PORT в .env и DB_URL при запуске
Java. При изменении имени БД/пользователя после первой инициализации существующий
volume сохраняет прежние параметры: переменные POSTGRES_* применяются при
создании нового кластера.

Требуется PostgreSQL 17+: миграции V2/V3 используют встроенную Unicode-коллацию
pg_c_utf8 для поиска русских имён и проверки уникальности email независимо от локали ОС.
Можно использовать уже установленный PostgreSQL вместо Docker: создай отдельную
базу и пользователя, укажи их через DB_URL, DB_USER, DB_PASSWORD.

## Запуск сервера

Spring Boot **не загружает .env автоматически**. Этот файл читает Docker Compose.
Для Java передай те же значения через переменные окружения терминала/IDE:

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/students'
$env:DB_USER = 'students'
$env:DB_PASSWORD = 'тот-же-пароль-что-в-.env'
# Пример генерации ключа для текущего терминала. При настройке запуска сохрани
# собственный ключ вне исходного кода, чтобы он не менялся при каждом старте.
$jwtBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
./mvnw.cmd spring-boot:run
```

При старте Flyway создаёт/обновляет схему. Launcher первоначально импортировал
122 студента; обычные старты не повторяют импорт. Проверка приложения и соединения с БД:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Ожидается `status: UP`. Студенческие HTTP-операции уже реализованы, но требуют
аутентификации; для анонимного запроса возвращается 401 в формате API.
USER/ADMIN читают, ADMIN изменяет; OPTIONS открыт. /api/auth/register создаёт
только USER, /api/auth/login выдаёт токены. Для первого ADMIN предусмотрен
отключённый по умолчанию bootstrap; параметры описаны в .env.example и authentication.md.
Сгенерированного пользователя/пароля Spring Security нет.
Frontend открывается Live Server на localhost:5500 или 127.0.0.1:5500 и обращается
к Java на 8080. Адрес API задаёт ../config.js; CORS_ALLOWED_ORIGINS задаёт
разрешённые адреса frontend. На login.html доступны вход и регистрация.
Swagger UI доступен на `/swagger-ui.html`, OpenAPI JSON — на `/v3/api-docs`,
YAML — на `/v3/api-docs.yaml`. Документация открыта без входа; операции API
проверяют JWT и права. В Authorize вставляется access-токен без префикса Bearer.
QUERY описан расширением x-query и выполняется через коллекцию Postman.
Рабочий API на этом компьютере использует порт 8081, сайт — 5500, БД — 55432.
В режиме start.cmd адреса берутся из .env и автоматически передаются frontend.
Самостоятельный запуск по примерам ниже использует заданные в них значения.

Остановить Java можно Ctrl+C; остановить PostgreSQL, сохранив данные:

```powershell
docker compose stop postgres
```

## Сборка и проверки

Модульные проверки JSON-контракта, ошибок, регистрации и rate limit без БД:

```powershell
./mvnw.cmd test
```

Интеграционные проверки используют настоящий PostgreSQL и отдельную БД.
Например, для запущенного выше Compose один раз создай тестовую БД:

```powershell
docker compose exec -T postgres psql -U students -d students -c 'CREATE DATABASE students_it;'
```

Затем:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/students_it'
$env:TEST_DB_USER = 'students'
$env:TEST_DB_PASSWORD = 'тот-же-пароль-что-в-.env'
./mvnw.cmd verify
```

Не указывай рабочую БД как TEST_DB_URL: в тестовой базе Flyway создаёт схему.
Изменения записей в MockMvc/репозиторных тестах откатываются транзакциями.
Отдельный HTTP-тест фиксирует изменения через настоящие транзакции и удаляет
только созданные им данные после завершения. Он входит через обычный login
и использует настоящие access/refresh JWT, без разрешающей тестовой цепочки.
Проверяются реальный
провайдер EclipseLink, миграция Flyway, смена ИСУ без смены внутреннего id,
ограничения уникальности/заселения, UUID и временные поля токенов, внешние ключи
и каскадное удаление, health, все методы API, поиск/сортировка/страницы,
валидация PATCH, единый JSON ошибок, права тестовых USER/ADMIN и отказ анонимным.
QUERY дополнительно проверен по настоящему HTTP через Tomcat.
AuthApiIT проверяет регистрацию, BCrypt, выдачу и проверку JWT, ротацию/отзыв,
назначение токенов, сроки, issuer/audience, подмену подписи, удалённого пользователя,
актуальную роль БД, bootstrap и CORS. StudentHttpIT также проверяет параллельное
обновление одного refresh-токена: успешно проходит только один запрос.
ApiDocumentationIT проверяет доступность UI/JSON/YAML, методы и схемы, публичную
и защищённую авторизацию, отсутствие утечки сущностей и разрешимость всех ссылок.
Коллекция Postman и пример окружения находятся в docs/postman; инструкция и
команда её отдельного запуска описаны в docs/api-checks.md.
StudentImportIT проверяет целостность первоначального импорта: весь файл
валидируется до записи, дубликаты/ошибки не оставляют частичных данных,
повторный импорт не перезаписывает существующие записи. При включении импорта
его завершение входит в readiness `/actuator/health/readiness`.

По умолчанию действует лимит 100 допущенных к обработке API-запросов за 60 секунд на IP.
При превышении возвращается 429 с Retry-After. OPTIONS не учитывается,
X-Forwarded-For не используется. Счётчик хранится в памяти одного процесса.
RATE_LIMIT_MAX_REQUESTS позволяет изменить предел; в интеграционных тестах
он повышен, а алгоритм лимита отдельно проверяется с управляемыми часами.
Публичные register/login/refresh/logout также учитываются, включая ошибки
неверного пароля и refresh; отклонённые фильтром JWT запросы до счётчика не доходят.

`test` запускает модульные тесты; `verify` также запускает классы `*IT` через
Failsafe. Без доступной тестовой PostgreSQL интеграционная проверка завершится
ошибкой. Для сборки JAR без интеграционного этапа достаточно `./mvnw.cmd package`.

## Импорт в Eclipse IDE

Если используешь Eclipse, выбери **File → Import → Maven → Existing Maven Projects**,
укажи папку `LR_3/server`. В Installed JREs и Java Build Path выбери JDK 17+.
В Run Configurations передай DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET и запусти
`ru.itmo.students.StudentsApplication`. EclipseLink не требует установленной IDE
Eclipse: это независимая библиотека провайдера JPA.

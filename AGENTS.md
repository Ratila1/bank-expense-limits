# AGENTS.md

Микросервис учёта расходных операций и месячных лимитов (Java 21, Spring Boot 4, PostgreSQL).
Подробности и допущения: `README.md`.

## Команды

- Сборка и все тесты (нужен Docker для Testcontainers): `./mvnw -B verify`
- Один тестовый класс: `./mvnw test -Dtest=ИмяКласса`
- Запуск локально: `./mvnw spring-boot:run` (БД поднимается из `compose.dev.yaml` сама)
- Весь стек в Docker (профиль `prod`): `docker compose up --build`
- Swagger UI: http://localhost:8080/swagger-ui.html

## Структура (`com.ratnikau.bankexpenselimits`)

`controller` (REST, тонкий слой) -> `service` (бизнес-логика) -> `repository` (Spring Data JPA).
Остальное: `domain` (сущности и enum), `dto` (records), `mapper` (MapStruct), `client` (внешний API курсов),
`config` (настройки и бины), `exception` (ProblemDetail), `util` (чистые функции без Spring:
`LimitRules`, `MonthRange`, `UsdConverter`).

## Правила, которые нельзя нарушать

- Схема БД меняется только новой миграцией Flyway в `src/main/resources/db/migration`
  (`V<N>__name.sql`). Применённые миграции не редактировать. См. скилл `create-flyway-migration`.
- Деньги и курсы только `BigDecimal`. Время только `Instant` и `OffsetDateTime`; текущее время берётся
  из бина `java.time.Clock`, а не из `Instant.now()`.
- Лимит привязан к паре (счёт `account_from`, категория). Перед чтением месячной суммы и сохранением
  транзакции обязательно берётся `SpendingLock.lock(...)` (advisory-блокировка) внутри DB-транзакции.
- Запрос курса во внешний API выполняется ДО открытия DB-транзакции, чтобы не держать блокировку.
- Часовой пояс границ месяца задаёт `app.zone` (по умолчанию UTC).
- Ошибки API возвращаются как `ProblemDetail` через `GlobalExceptionHandler`.
- Секреты (ключ Twelve Data, пароли) только через переменные окружения; не логировать и не коммитить.
  Файл `.env` в git не попадает, образец в `.env.example`.
- DTO и сущности связывает MapStruct (`unmappedTargetPolicy = ERROR`), ручные `from(...)` не добавлять.

## Тесты

JUnit 5, AssertJ, Mockito, Testcontainers (`@ServiceConnection`), WireMock для внешнего API.
Интеграционные тесты называть `*Test` (не `*IT`): surefire запускает только такие.
Профиль тестов: `@ActiveProfiles("test")`.
---
name: create-flyway-migration
description: Create or change the database schema of this project through a new Flyway migration (V<N>__name.sql in src/main/resources/db/migration) and keep JPA entities, DTOs and MapStruct mappers in sync. Use when a task mentions a new table, column, index, constraint, enum value or any other schema change (миграция, схема БД, Flyway). Do not use for code changes that do not touch the database schema.
---

# Создание миграции Flyway в bank-expense-limits

Схему БД создаёт только Flyway. Hibernate работает в режиме `spring.jpa.hibernate.ddl-auto=validate`
и лишь сверяет сущности со схемой, поэтому любое изменение схемы оформляется новой миграцией.

## Шаги

1. **Определи следующий номер.** Посмотри файлы в `src/main/resources/db/migration/` и возьми
   максимальный номер плюс один. Сейчас там есть `V1__init_schema.sql`.
2. **Назови файл** `V<N>__<описание_snake_case>.sql`, например `V2__add_transaction_comment.sql`.
   Заглавная `V` и **ровно два** подчёркивания после номера. С одним подчёркиванием Flyway не выдаёт
   ошибку, а молча пропускает файл (в логе: «SQL migrations were detected but not run because they
   did not follow the filename convention»).
3. **Никогда не правь уже созданные миграции** (`V1__...`, `V2__...`): Flyway хранит их контрольные
   суммы, и изменённый файл ломает запуск (`Migration checksum mismatch`). Любое изменение только новой миграцией.
4. **Пиши SQL по правилам проекта:**
   - деньги: `NUMERIC(19, 2)`, курсы: `NUMERIC(19, 8)`, в Java это `BigDecimal`, не `double`;
   - время: `TIMESTAMPTZ`, в Java это `Instant`;
   - номера счетов: `VARCHAR(10)` и `CHECK (col ~ '^[0-9]{10}$')`;
   - `Category` хранится **строчными** буквами (`'product'`, `'service'`) через `CategoryConverter`,
     поэтому в `CHECK` пиши строчные; `TransactionStatus` и `RateKind` хранятся именами enum
     (`@Enumerated(EnumType.STRING)`), в `CHECK` они **заглавные**;
   - новое значение enum: в той же миграции пересоздай ограничение
     (`DROP CONSTRAINT <таблица>_<колонка>_check`, затем `ADD CONSTRAINT`);
   - имена: первичный ключ `BIGSERIAL`, индексы `idx_<таблица>_<колонки>`, уникальные `uq_<описание>`;
   - новый `NOT NULL` столбец в таблице с данными: сначала добавь столбец с `DEFAULT` или nullable,
     заполни значения, затем поставь `NOT NULL`, всё в одной миграции.
5. **Синхронизируй код:**
   - сущность в `com.ratnikau.bankexpenselimits.domain`: `@Column(name = "...", precision = .., scale = ..)`;
   - если поле видно в API: record в `dto` и `@Mapping` в `mapper` (`TransactionMapper`, `LimitMapper`).
     В `CentralMapperConfig` стоит `unmappedTargetPolicy = ERROR`, поэтому незамапленное поле даёт ошибку компиляции:
     добавь `@Mapping(target = "...", source = "...")` или `ignore = true`.
6. **Проверь:** `./mvnw -B verify` (нужен запущенный Docker). Тест `BankexpenselimitsApplicationTests`
   поднимает чистый Postgres в Testcontainers, применяет все миграции и запускает `validate`.
   Диагностика:
   - `No migrations found` или «did not follow the filename convention»: неверное имя или папка файла;
   - `Schema-validation: missing table [...]` или `wrong column type`: сущность и миграция расходятся;
   - `Migration checksum mismatch`: была изменена уже применённая миграция;
   - локальная БД из `docker compose` помнит старую схему: `docker compose down -v` и запуск заново.
7. **Закоммить** миграцию вместе с изменениями сущности, DTO и маппера одним коммитом:
   `feat(db): <что изменилось>`.

## Пример

Задача: добавить комментарий к транзакции.

`src/main/resources/db/migration/V2__add_transaction_comment.sql`:

    ALTER TABLE transactions ADD COLUMN comment VARCHAR(255);

В `Transaction`: `@Column(name = "comment", length = 255) private String comment;`.
Если комментарий приходит в запросе, добавь его в `TransactionRequest` и `TransactionResponse`,
иначе добавь `@Mapping(target = "comment", ignore = true)` в `TransactionMapper.toEntity`.
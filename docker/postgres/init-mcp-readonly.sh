#!/bin/bash
# Выполняется один раз при первой инициализации тома БД (docker-entrypoint-initdb.d).
# Создаёт пользователя для MCP-сервера: только SELECT, любые транзакции только на чтение.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v mcp_password="${MCP_DB_PASSWORD:-mcp_readonly}" \
     -v dbname="$POSTGRES_DB" \
     -v owner="$POSTGRES_USER" <<'EOSQL'
CREATE ROLE mcp_readonly LOGIN PASSWORD :'mcp_password';
ALTER ROLE mcp_readonly SET default_transaction_read_only = on;
ALTER ROLE mcp_readonly SET statement_timeout = '5s';
GRANT CONNECT ON DATABASE :"dbname" TO mcp_readonly;
GRANT USAGE ON SCHEMA public TO mcp_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO mcp_readonly;
-- Таблицы создаст Flyway от имени основного пользователя уже после этого скрипта
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public GRANT SELECT ON TABLES TO mcp_readonly;
EOSQL
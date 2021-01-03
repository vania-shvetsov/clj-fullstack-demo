#!/usr/bin/env bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE postgres_test;
    GRANT ALL PRIVILEGES ON DATABASE postgres_test TO postgres;
EOSQL

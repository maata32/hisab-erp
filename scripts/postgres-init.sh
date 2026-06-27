#!/bin/sh
# Auto-run by the official postgres image on first boot (docker-entrypoint-initdb.d).
#
# Creates the NON-SUPERUSER application runtime role (hisaberp_app). The Spring datasource
# connects as hisaberp_app so PostgreSQL RLS (defense layer 2) is enforced at runtime;
# Liquibase connects as the owner/admin role ($POSTGRES_USER). Table/sequence GRANTs to
# hisaberp_app are applied by Liquibase migration 0078-grant-app-role.xml (reliable for both
# fresh and existing databases, regardless of ALTER DEFAULT PRIVILEGES timing).
#
# Passwords come from the environment so production uses strong secrets (dev defaults shown):
#   APP_DB_PASSWORD       password for hisaberp_app       (default: hisaberp)
#   READONLY_DB_PASSWORD  password for hisaberp_readonly  (default: hisaberp_readonly)
set -e

APP_DB_PASSWORD="${APP_DB_PASSWORD:-hisaberp}"
READONLY_DB_PASSWORD="${READONLY_DB_PASSWORD:-hisaberp_readonly}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
  CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
  CREATE EXTENSION IF NOT EXISTS "pg_trgm";
  CREATE EXTENSION IF NOT EXISTS "pgcrypto";

  -- Application runtime role: non-superuser, RLS-enforced. Idempotent so the password is
  -- kept in sync with APP_DB_PASSWORD even if the role already exists.
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hisaberp_app') THEN
      CREATE ROLE hisaberp_app LOGIN PASSWORD '${APP_DB_PASSWORD}'
        NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    ELSE
      ALTER ROLE hisaberp_app WITH LOGIN PASSWORD '${APP_DB_PASSWORD}'
        NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
  END
  \$\$;

  GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO hisaberp_app;

  -- Integration-test database (Liquibase connects as ${POSTGRES_USER}, app as hisaberp_app).
  CREATE DATABASE hisaberp_test WITH OWNER ${POSTGRES_USER} ENCODING 'UTF8' TEMPLATE template0;
  GRANT CONNECT ON DATABASE hisaberp_test TO hisaberp_app;

  -- Optional read-only role for analytics / Metabase (V2).
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hisaberp_readonly') THEN
      CREATE ROLE hisaberp_readonly LOGIN PASSWORD '${READONLY_DB_PASSWORD}'
        NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    ELSE
      ALTER ROLE hisaberp_readonly WITH LOGIN PASSWORD '${READONLY_DB_PASSWORD}'
        NOSUPERUSER NOBYPASSRLS;
    END IF;
  END
  \$\$;
SQL

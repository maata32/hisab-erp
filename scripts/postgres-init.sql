-- Auto-run by the official postgres image on first boot.
-- Safe to re-run.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Application runtime role (used by Spring datasource in test and prod)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'minierp_app') THEN
    CREATE ROLE minierp_app LOGIN PASSWORD 'minierp';
  END IF;
END
$$;

GRANT CONNECT ON DATABASE minierp TO minierp_app;

-- Auto-grant future tables/sequences created by minierp to minierp_app
ALTER DEFAULT PRIVILEGES FOR ROLE minierp IN SCHEMA public
    GRANT ALL ON TABLES TO minierp_app;
ALTER DEFAULT PRIVILEGES FOR ROLE minierp IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO minierp_app;

-- Integration test database (Liquibase connects as minierp, app connects as minierp_app)
CREATE DATABASE minierp_test WITH OWNER minierp ENCODING 'UTF8' TEMPLATE template0;
GRANT CONNECT ON DATABASE minierp_test TO minierp_app;

-- Optional: dedicated read-only role for analytics / Metabase (V2)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'minierp_readonly') THEN
    CREATE ROLE minierp_readonly LOGIN PASSWORD 'minierp_readonly';
  END IF;
END
$$;

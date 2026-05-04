-- Auto-run by the official postgres image on first boot.
-- Safe to re-run.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Optional: dedicated read-only role for analytics / Metabase (V2)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'minierp_readonly') THEN
    CREATE ROLE minierp_readonly LOGIN PASSWORD 'minierp_readonly';
  END IF;
END
$$;

-- General Spring integration tests retain the legacy fixture chain. Load the
-- production H2 V305 migration after those fixtures so tests exercise the same
-- lightweight accounting schema and catalog as a fresh baseline database.
RUNSCRIPT FROM 'classpath:db/migration-h2/V305__implement_lightweight_construction_accounting_catalog.sql';

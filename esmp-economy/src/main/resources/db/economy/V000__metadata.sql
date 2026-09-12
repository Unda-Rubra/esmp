-- Bootstrap only. Runtime validates existing version, currency and period before other migrations.
CREATE TABLE IF NOT EXISTS economy_metadata (
    id INTEGER PRIMARY KEY CHECK(id=1),
    schema_version INTEGER NOT NULL,
    currency_id TEXT NOT NULL UNIQUE,
    period_ticks INTEGER NOT NULL CHECK(period_ticks>0)
);

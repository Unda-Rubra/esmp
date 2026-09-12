-- Upgrade schema 1 to schema 2 without rewriting ledger data or replay identities.
CREATE TABLE economy_starting_grants (
    player_uuid TEXT PRIMARY KEY,
    amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount>0),
    eligible_tick INTEGER NOT NULL,
    next_period INTEGER NOT NULL CHECK(next_period>=0),
    transaction_id TEXT UNIQUE REFERENCES economy_transactions(transaction_id)
);

CREATE INDEX economy_starting_grants_due
    ON economy_starting_grants(next_period,eligible_tick)
    WHERE transaction_id IS NULL;

UPDATE economy_metadata SET schema_version=2 WHERE id=1;

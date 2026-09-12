-- Schema 1. Runtime binds the initial metadata row before applying this resource.
CREATE TABLE economy_accounts (
    account_key TEXT PRIMARY KEY,
    currency_id TEXT NOT NULL REFERENCES economy_metadata(currency_id),
    kind TEXT NOT NULL CHECK(kind IN ('WALLET','TREASURY')),
    owner_uuid TEXT NOT NULL,
    balance INTEGER NOT NULL DEFAULT 0 CHECK(typeof(balance)='integer' AND balance>=0),
    UNIQUE(currency_id,kind,owner_uuid)
);

CREATE TABLE economy_budgets (
    period INTEGER PRIMARY KEY CHECK(period>=0),
    budget_limit INTEGER NOT NULL CHECK(typeof(budget_limit)='integer' AND budget_limit>=0),
    issued INTEGER NOT NULL DEFAULT 0
        CHECK(typeof(issued)='integer' AND issued>=0 AND issued<=budget_limit)
);

CREATE TABLE economy_transactions (
    transaction_id TEXT PRIMARY KEY,
    currency_id TEXT NOT NULL REFERENCES economy_metadata(currency_id),
    movement TEXT NOT NULL CHECK(movement IN ('TRANSFER','ISSUANCE','SINK')),
    actor TEXT NOT NULL,
    source_account TEXT REFERENCES economy_accounts(account_key),
    target_account TEXT REFERENCES economy_accounts(account_key),
    amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount>0),
    reason TEXT NOT NULL,
    note TEXT NOT NULL,
    game_tick INTEGER NOT NULL,
    budget_period INTEGER REFERENCES economy_budgets(period),
    source_after INTEGER,
    target_after INTEGER,
    CHECK(
        (movement='TRANSFER' AND source_account IS NOT NULL AND target_account IS NOT NULL
            AND source_account<>target_account AND budget_period IS NULL)
        OR (movement='ISSUANCE' AND source_account IS NULL AND target_account IS NOT NULL
            AND budget_period IS NOT NULL)
        OR (movement='SINK' AND source_account IS NOT NULL AND target_account IS NULL
            AND budget_period IS NULL)
    )
);

CREATE INDEX economy_transactions_source ON economy_transactions(source_account,game_tick);
CREATE INDEX economy_transactions_target ON economy_transactions(target_account,game_tick);

CREATE TABLE economy_allowances (
    treasury_uuid TEXT NOT NULL,
    governor_uuid TEXT NOT NULL,
    ownership_revision INTEGER NOT NULL,
    promotion_id TEXT,
    remaining INTEGER NOT NULL CHECK(typeof(remaining)='integer' AND remaining>=0),
    PRIMARY KEY(treasury_uuid,governor_uuid)
);

CREATE TABLE economy_allowance_events (
    operation_id TEXT PRIMARY KEY,
    actor TEXT NOT NULL,
    treasury_uuid TEXT NOT NULL,
    governor_uuid TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount>=0),
    ownership_revision INTEGER NOT NULL,
    promotion_id TEXT,
    game_tick INTEGER NOT NULL
);

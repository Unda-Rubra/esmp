CREATE TABLE IF NOT EXISTS activity_policy (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    definition TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS settlement_activity (
    settlement_id TEXT PRIMARY KEY REFERENCES settlements(settlement_id),
    score REAL NOT NULL,
    anchor_tick INTEGER NOT NULL,
    inactive_tick INTEGER,
    day INTEGER NOT NULL,
    credited REAL NOT NULL,
    last_tick INTEGER,
    category TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS activity_actors (
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    actor_uuid TEXT NOT NULL,
    day INTEGER NOT NULL,
    credited REAL NOT NULL,
    build_tick INTEGER,
    interaction_tick INTEGER,
    PRIMARY KEY (settlement_id, actor_uuid)
);

CREATE TABLE IF NOT EXISTS activity_pauses (
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    from_tick INTEGER NOT NULL,
    until_tick INTEGER NOT NULL,
    PRIMARY KEY (settlement_id, from_tick)
);

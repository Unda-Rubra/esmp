CREATE TABLE IF NOT EXISTS game_clock (
    id INTEGER PRIMARY KEY CHECK(id=1),
    source_uuid TEXT NOT NULL,
    raw_tick INTEGER NOT NULL,
    game_tick INTEGER NOT NULL CHECK(game_tick>=0)
);

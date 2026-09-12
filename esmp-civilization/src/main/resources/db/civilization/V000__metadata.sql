-- Bootstrap metadata before runtime reads the Civilization schema version.
CREATE TABLE IF NOT EXISTS civilization_schema (
    id INTEGER PRIMARY KEY CHECK(id=1),
    version INTEGER NOT NULL
);

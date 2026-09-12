CREATE TABLE IF NOT EXISTS land_value_metadata (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    schema_version INTEGER NOT NULL
);

INSERT OR IGNORE INTO land_value_metadata (id, schema_version) VALUES (1, 1);

CREATE TABLE IF NOT EXISTS land_value_policies (
    version INTEGER PRIMARY KEY CHECK (version > 0),
    base_value INTEGER NOT NULL CHECK (base_value > 0),
    relief_penalty INTEGER NOT NULL CHECK (relief_penalty >= 0),
    max_relief_penalty INTEGER NOT NULL
        CHECK (max_relief_penalty >= 0 AND max_relief_penalty < base_value),
    shore_bonus INTEGER NOT NULL CHECK (shore_bonus >= 0)
);

CREATE TABLE IF NOT EXISTS land_value_profiles (
    world_instance_id TEXT NOT NULL REFERENCES world_instances(world_instance_id),
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    valuation_version INTEGER NOT NULL REFERENCES land_value_policies(version),
    base_value INTEGER NOT NULL CHECK (typeof(base_value) = 'integer' AND base_value > 0),
    valuation_class TEXT NOT NULL
        CHECK (valuation_class IN ('FLAT', 'RUGGED', 'SHORE', 'WATER')),
    PRIMARY KEY (world_instance_id, chunk_x, chunk_z)
);

CREATE TABLE world_definitions (
    world_definition_id TEXT PRIMARY KEY,
    module_id TEXT NOT NULL,
    environment TEXT NOT NULL CHECK (environment IN ('NORMAL', 'NETHER', 'THE_END'))
);
CREATE TABLE world_instances (
    world_instance_id TEXT PRIMARY KEY,
    world_definition_id TEXT NOT NULL REFERENCES world_definitions(world_definition_id),
    bukkit_world_uuid TEXT NOT NULL UNIQUE,
    world_name TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX world_instances_definition ON world_instances(world_definition_id);

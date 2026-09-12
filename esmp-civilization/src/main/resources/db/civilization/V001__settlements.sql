-- V000__metadata.sql is applied before the runtime version check.
CREATE TABLE settlements (
    settlement_id TEXT PRIMARY KEY,
    world_instance_id TEXT NOT NULL REFERENCES world_instances(world_instance_id),
    name TEXT NOT NULL,
    name_key TEXT NOT NULL,
    owner_uuid TEXT,
    created_at INTEGER NOT NULL,
    ownership_revision INTEGER NOT NULL,
    chunk_revision INTEGER NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('OWNED','RELEASED')),
    UNIQUE(settlement_id,world_instance_id),
    CHECK((status='OWNED' AND owner_uuid IS NOT NULL) OR (status='RELEASED' AND owner_uuid IS NULL))
);
CREATE UNIQUE INDEX settlements_owned_names ON settlements(name_key) WHERE status='OWNED';
CREATE TABLE settlement_members (
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    player_uuid TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('MANAGER','MEMBER')),
    PRIMARY KEY(settlement_id,player_uuid)
);
CREATE TABLE settlement_chunks (
    world_instance_id TEXT NOT NULL,
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    settlement_id TEXT NOT NULL,
    PRIMARY KEY(world_instance_id,chunk_x,chunk_z),
    FOREIGN KEY(settlement_id,world_instance_id) REFERENCES settlements(settlement_id,world_instance_id)
);
CREATE INDEX settlement_chunks_owner ON settlement_chunks(settlement_id);
CREATE TABLE settlement_offers (
    offer_id TEXT PRIMARY KEY,
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    type TEXT NOT NULL CHECK(type IN ('INVITE','PROMOTE','TRANSFER','ABANDON')),
    issuer_uuid TEXT NOT NULL,
    recipient_uuid TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    ownership_revision INTEGER NOT NULL,
    chunk_revision INTEGER NOT NULL,
    state TEXT NOT NULL CHECK(state IN ('PENDING','ACCEPTED','CANCELLED','DECLINED','EXPIRED'))
);
CREATE UNIQUE INDEX settlement_pending_offer ON settlement_offers(settlement_id,type,recipient_uuid) WHERE state='PENDING';
CREATE TABLE settlement_events (
    event_id TEXT PRIMARY KEY,
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    actor_uuid TEXT NOT NULL,
    type TEXT NOT NULL,
    detail TEXT NOT NULL,
    occurred_at INTEGER NOT NULL,
    delivered INTEGER NOT NULL DEFAULT 0 CHECK(delivered IN (0,1))
);
CREATE INDEX settlement_events_history ON settlement_events(settlement_id,occurred_at);
CREATE INDEX settlement_events_outbox ON settlement_events(delivered);
INSERT INTO civilization_schema(id,version) VALUES(1,1);

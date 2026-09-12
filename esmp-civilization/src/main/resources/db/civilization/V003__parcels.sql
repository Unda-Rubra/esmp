CREATE TABLE settlement_bans(
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    player_uuid TEXT NOT NULL,
    banned_by TEXT NOT NULL,
    created_tick INTEGER NOT NULL,
    PRIMARY KEY(settlement_id,player_uuid)
);

CREATE TABLE parcels(
    parcel_id TEXT PRIMARY KEY,
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    name TEXT NOT NULL,
    name_key TEXT NOT NULL,
    revision INTEGER NOT NULL,
    UNIQUE(settlement_id,name_key)
);

CREATE TABLE parcel_cells(
    parcel_id TEXT NOT NULL REFERENCES parcels(parcel_id) ON DELETE CASCADE,
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    PRIMARY KEY(settlement_id,x,y,z)
);

CREATE INDEX parcel_cells_owner ON parcel_cells(parcel_id);

CREATE TABLE parcel_holders(
    parcel_id TEXT NOT NULL REFERENCES parcels(parcel_id) ON DELETE CASCADE,
    player_uuid TEXT NOT NULL,
    PRIMARY KEY(parcel_id,player_uuid)
);

CREATE TABLE parcel_policy(
    parcel_id TEXT NOT NULL REFERENCES parcels(parcel_id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    relation TEXT NOT NULL,
    PRIMARY KEY(parcel_id,action,relation)
);

CREATE TABLE parcel_requests(
    request_id TEXT PRIMARY KEY,
    settlement_id TEXT NOT NULL,
    parcel_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    payload TEXT NOT NULL,
    parcel_revision INTEGER NOT NULL,
    ownership_revision INTEGER NOT NULL,
    chunk_revision INTEGER NOT NULL,
    security_revision INTEGER NOT NULL,
    issuer TEXT NOT NULL,
    expires_tick INTEGER NOT NULL,
    state TEXT NOT NULL
);

CREATE TABLE parcel_approvals(
    request_id TEXT NOT NULL REFERENCES parcel_requests(request_id),
    player_uuid TEXT NOT NULL,
    PRIMARY KEY(request_id,player_uuid)
);

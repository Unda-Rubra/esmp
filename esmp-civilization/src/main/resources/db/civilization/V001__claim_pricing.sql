CREATE TABLE player_land_progress(
    player_uuid TEXT PRIMARY KEY,
    claims_used INTEGER NOT NULL CHECK(typeof(claims_used)='integer' AND claims_used>=0),
    first_founding_used INTEGER NOT NULL CHECK(first_founding_used IN(0,1))
);
-- Development cutover: prior founding/claim actors have used their personal early actions.
-- No retroactive debit. Historical governor actions cannot reliably reconstruct owner epochs.
INSERT INTO player_land_progress
SELECT actor_uuid,count(*),max(CASE WHEN type='FOUNDED' THEN 1 ELSE 0 END)
FROM settlement_events WHERE type IN('FOUNDED','CHUNK_CLAIMED') GROUP BY actor_uuid;
CREATE TABLE claim_quotes(
    quote_id TEXT PRIMARY KEY,
    actor_uuid TEXT NOT NULL,
    owner_uuid TEXT NOT NULL,
    settlement_id TEXT NOT NULL,
    world_instance_id TEXT NOT NULL REFERENCES world_instances(world_instance_id),
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    founding INTEGER NOT NULL CHECK(founding IN(0,1)),
    payer_kind TEXT NOT NULL CHECK(payer_kind IN('WALLET','TREASURY')),
    payer_uuid TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount>0),
    context TEXT NOT NULL,
    summary TEXT NOT NULL,
    expires_tick INTEGER NOT NULL,
    purchased INTEGER NOT NULL CHECK(purchased IN(0,1))
);
CREATE INDEX claim_quotes_actor ON claim_quotes(actor_uuid,purchased);
INSERT INTO claim_pricing_schema VALUES(1,1);

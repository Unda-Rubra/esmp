CREATE TABLE settlement_details (
    settlement_id TEXT PRIMARY KEY REFERENCES settlements(settlement_id),
    number INTEGER NOT NULL UNIQUE,
    official_name TEXT,
    certified INTEGER NOT NULL,
    security_revision INTEGER NOT NULL
);
-- SQLite rowid is the original insertion-order tie breaker, including released settlements.
INSERT INTO settlement_details(settlement_id,number,official_name,certified,security_revision)
SELECT settlement_id,row_number() OVER(ORDER BY rowid),name,1,0 FROM settlements;
CREATE TABLE settlement_members_new (
    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
    player_uuid TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('GOVERNOR','CITIZEN')),
    PRIMARY KEY(settlement_id,player_uuid)
);
INSERT INTO settlement_members_new(settlement_id,player_uuid,role)
SELECT settlement_id,player_uuid,
    CASE role WHEN 'MANAGER' THEN 'GOVERNOR' WHEN 'MEMBER' THEN 'CITIZEN' ELSE role END
FROM settlement_members;
DROP TABLE settlement_members;
ALTER TABLE settlement_members_new RENAME TO settlement_members;
-- ParcelService separately applies V003__parcels.sql in this transaction.
-- Runtime advances version to 3 only after both migrations succeed.

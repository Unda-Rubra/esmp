ALTER TABLE settlements ADD COLUMN founded_tick INTEGER NOT NULL DEFAULT 0;
ALTER TABLE settlement_events ADD COLUMN game_tick INTEGER;
-- In the same transaction, runtime sets founded_tick from GameClock and converts
-- every legacy absolute offer deadline using one wall-clock cutover, ceiling to
-- 50ms ticks with checked arithmetic. Only then does it advance version to 2.
-- Existing event game_tick values intentionally remain NULL.

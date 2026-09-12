package xdu.undarubra.esmp.economy.api;

import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.economy.CurrencyDefinition;
import xdu.undarubra.esmp.economy.LedgerPolicy;
import xdu.undarubra.esmp.economy.LedgerService;
import xdu.undarubra.esmp.economy.LedgerService.TreasuryAuthorizer;
import xdu.undarubra.esmp.economy.persistence.JooqLedgerRepository;

/** Public composition entry points for economy services. */
public final class EconomyServices {
    private EconomyServices() {}

    /**
     * Accepts WorldStore through its UnitOfWork port; does not initialize or migrate at
     * construction.
     */
    public static LedgerService ledger(
            UnitOfWork database,
            GameClock clock,
            CurrencyDefinition currency,
            LedgerPolicy policy,
            TreasuryAuthorizer treasury) {
        return new LedgerService(
                new JooqLedgerRepository(database, clock, currency, policy, treasury));
    }
}

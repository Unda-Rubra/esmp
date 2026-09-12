package xdu.undarubra.esmp.civilization.api;

import xdu.undarubra.esmp.civilization.persistence.JooqParcelRepository;
import xdu.undarubra.esmp.civilization.settlement.ParcelCell;
import xdu.undarubra.esmp.civilization.settlement.ParcelView;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.sql.SQLException;
import java.util.UUID;

/** Read-only governance projections in an existing transaction; never starts another one. */
public final class GovernanceQueries {
    private GovernanceQueries() {}

    public static ParcelView at(Transaction transaction, UUID settlement, ParcelCell cell)
            throws SQLException {
        return JooqParcelRepository.at(transaction, settlement, cell);
    }

    public static boolean banned(Transaction transaction, UUID settlement, UUID actor)
            throws SQLException {
        return JooqParcelRepository.banned(transaction, settlement, actor);
    }

    public static ParcelView read(Transaction transaction, UUID parcel) throws SQLException {
        return JooqParcelRepository.read(transaction, parcel);
    }
}
